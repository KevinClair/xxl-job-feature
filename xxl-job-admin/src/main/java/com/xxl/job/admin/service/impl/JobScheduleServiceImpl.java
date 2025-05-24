package com.xxl.job.admin.service.impl;

import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.thread.JobTriggerPoolHelper;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.util.TriggerUtil;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobLockDao;
import com.xxl.job.admin.service.JobScheduleService;
import com.xxl.job.common.constant.Constants;
import com.xxl.job.common.enums.MisfireStrategyEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.*;

@Service
public class JobScheduleServiceImpl implements JobScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(JobScheduleServiceImpl.class);

    private final XxlJobLockDao jobLockDao;

    private final XxlJobInfoDao jobInfoDao;

    private final JobTriggerPoolHelper triggerPoolHelper;

    public JobScheduleServiceImpl(final XxlJobLockDao jobLockDao, final XxlJobInfoDao jobInfoDao,
                                  final JobTriggerPoolHelper triggerPoolHelper) {
        this.jobLockDao = jobLockDao;
        this.jobInfoDao = jobInfoDao;
        this.triggerPoolHelper = triggerPoolHelper;
    }

    @Override
    @Transactional
    public boolean execute(Integer preReadCount, Map<Integer, List<Integer>> ringData) throws ParseException {
        // 开始分布式锁
        jobLockDao.lock();
        long nowTime = System.currentTimeMillis();
        List<XxlJobInfo> scheduleList = jobInfoDao.scheduleJobQuery(nowTime + Constants.PRE_READ_MS, preReadCount);
        if (scheduleList.isEmpty()){
            return false;
        }
        // |         now-PRE_READ_MS                       now                      now+PRE_READ_MS
        // |-------A--------|----------------B--------------|-----------------C-------------|
        // scheduleList查询的数据为所有调度时间小于now+PRE_READ_MS的数据，进行遍历
        for (XxlJobInfo jobInfo : scheduleList) {
            // time-ring jump
            if (nowTime > jobInfo.getTriggerNextTime() + Constants.PRE_READ_MS) {
                // 当前任务的调度时间，在A范围内，根据调度过期策略选择忽略或者立即执行一次，并且刷新下一次的执行时间

                // 2.1、trigger-expire > 5s：pass && make next-trigger-time
                logger.warn(">>>>>>>>>>> xxl-job, schedule misfire, jobId = " + jobInfo.getId());

                // 1、misfire match
                MisfireStrategyEnum
                    misfireStrategyEnum = MisfireStrategyEnum.match(jobInfo.getMisfireStrategy(), MisfireStrategyEnum.DO_NOTHING);
                if (MisfireStrategyEnum.FIRE_ONCE_NOW == misfireStrategyEnum) {
                    // FIRE_ONCE_NOW 》 trigger
                    triggerPoolHelper.trigger(jobInfo.getId(), TriggerTypeEnum.MISFIRE, -1, null, null, null);
                    logger.debug(">>>>>>>>>>> xxl-job, schedule push trigger : jobId = " + jobInfo.getId() );
                }

                // 2、fresh next
                refreshNextValidTime(jobInfo, new Date());

            } else if (nowTime > jobInfo.getTriggerNextTime()) {
                // 当前任务的调度时间，在B范围内，直接执行，并且刷新下一次的执行时间
                // 2.2、trigger-expire < 5s：direct-trigger && make next-trigger-time
                // 1、trigger
                triggerPoolHelper.trigger(jobInfo.getId(), TriggerTypeEnum.CRON, -1, null, null, null);
                logger.debug(">>>>>>>>>>> xxl-job, schedule push trigger : jobId = " + jobInfo.getId() );

                // 2、fresh next
                refreshNextValidTime(jobInfo, new Date());

                // next-trigger-time in 5s, pre-read again
                // 如果此时当前调度任务的下一次执行时间依然是小于nowTime + PRE_READ_MS
                // todo 补充注释
                if (jobInfo.getTriggerStatus()==1 && nowTime + Constants.PRE_READ_MS > jobInfo.getTriggerNextTime()) {

                    // 1、make ring second
                    executeRingTask(ringData, jobInfo);

                }
            } else {
                // 当前任务的调度时间在C范围内
                // 2.3、trigger-pre-read：time-ring trigger && make next-trigger-time
                executeRingTask(ringData, jobInfo);
            }
        }

        // 3、update trigger info
        for (XxlJobInfo jobInfo: scheduleList) {
            jobInfoDao.scheduleUpdate(jobInfo);
        }
        return true;
    }

    /**
     * 计算任务，将任务时间放到ringData中，让ringThread处理
     *
     * @param ringData
     * @param jobInfo
     * @throws ParseException
     */
    private void executeRingTask(Map<Integer, List<Integer>> ringData, XxlJobInfo jobInfo) throws ParseException {
        // 1、make ring second
        int ringSecond = (int)((jobInfo.getTriggerNextTime()/1000)%60);

        // 2、push time ring
        pushTimeRing(ringSecond, jobInfo.getId(), ringData);

        // 3、fresh next
        refreshNextValidTime(jobInfo, new Date(jobInfo.getTriggerNextTime()));
    }

    private void refreshNextValidTime(XxlJobInfo jobInfo, Date fromTime) throws ParseException {
        Date nextValidTime = TriggerUtil.generateNextValidTime(jobInfo, fromTime);
        if (nextValidTime != null) {
            jobInfo.setTriggerLastTime(jobInfo.getTriggerNextTime());
            jobInfo.setTriggerNextTime(nextValidTime.getTime());
        } else {
            jobInfo.setTriggerStatus(0);
            jobInfo.setTriggerLastTime(0);
            jobInfo.setTriggerNextTime(0);
            logger.warn(">>>>>>>>>>> xxl-job, refreshNextValidTime fail for job: jobId={}, scheduleType={}, scheduleConf={}",
                jobInfo.getId(), jobInfo.getScheduleType(), jobInfo.getScheduleConf());
        }
    }

    private void pushTimeRing(int ringSecond, int jobId, Map<Integer, List<Integer>> ringData){
        // push async ring
        List<Integer> ringItemData = ringData.computeIfAbsent(ringSecond, k -> new ArrayList<>());
        ringItemData.add(jobId);

        logger.debug(">>>>>>>>>>> xxl-job, schedule push time-ring : " + ringSecond + " = " + Arrays.asList(ringItemData) );
    }
}
