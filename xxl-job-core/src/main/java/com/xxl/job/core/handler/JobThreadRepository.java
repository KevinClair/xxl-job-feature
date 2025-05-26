package com.xxl.job.core.handler;

import com.xxl.job.core.thread.JobThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Job Thread Repository.
 */
public class JobThreadRepository {

    private static final Logger logger = LoggerFactory.getLogger(JobThreadRepository.class);

    private static final ConcurrentMap<Integer, JobThread> jobThreadRepository = new ConcurrentHashMap<>();

    /**
     * registry new job thread.
     *
     * 此处逻辑应该调整为通过BeanDefinitionRegistry注册一个JobThread的Bean对象。name就是job的bean名称。
     * 之后在使用时通过ApplicationContext.getBean(jobName)获取JobThread对象。
     * 不需要时候通过BeanDefinitionRegistry动态移除JobThread对象。
     *
     * @param jobId           id of job.
     * @param handler         handler of job.
     * @param removeOldReason the reason of removing old thread.
     * @return
     */
    public static JobThread registerJobThread(int jobId, IJobHandler handler, String removeOldReason){
        JobThread newJobThread = new JobThread(jobId, handler);
        newJobThread.start();
        logger.info(">>>>>>>>>>> xxl-job regist JobThread success, jobId:{}, handler:{}", new Object[]{jobId, handler});

        JobThread oldJobThread = jobThreadRepository.put(jobId, newJobThread);	// putIfAbsent | oh my god, map's put method return the old value!!!
        if (Objects.nonNull(oldJobThread)) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
        }

        return newJobThread;
    }

    /**
     * remove old job thread.
     *
     * @param jobId           id of job.
     * @param removeOldReason the reason of removing old thread.
     * @return
     */
    public static JobThread removeJobThread(int jobId, String removeOldReason){
        JobThread oldJobThread = jobThreadRepository.remove(jobId);
        if (Objects.nonNull(oldJobThread)) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();

            return oldJobThread;
        }
        return null;
    }

    /**
     * destroy method.
     */
    public static void destroy(){
        // destroy jobThreadRepository
        if (jobThreadRepository.size() > 0) {
            for (Map.Entry<Integer, JobThread> item: jobThreadRepository.entrySet()) {
                JobThread oldJobThread = removeJobThread(item.getKey(), "web container destroy and kill the job.");
                // wait for job thread push result to callback queue
                if (oldJobThread != null) {
                    try {
                        oldJobThread.join();
                    } catch (InterruptedException e) {
                        logger.error(">>>>>>>>>>> xxl-job, JobThread destroy(join) error, jobId:{}", item.getKey(), e);
                    }
                }
            }
            jobThreadRepository.clear();
        }
    }

    /**
     * load job thread by id of job.
     *
     * @param jobId id of job.
     * @return Job thread {@link JobThread}
     */
    public static JobThread loadJobThread(int jobId){
        return jobThreadRepository.get(jobId);
    }
}
