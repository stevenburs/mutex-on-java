package ampedandwired.bamboo.plugin.mutex;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.plugins.PostChainAction;
import com.atlassian.bamboo.chains.plugins.PreChainAction;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.builder.LifeCycleState;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlanMutexPreAndPostChainAction implements PreChainAction, PostChainAction {
  public static final Logger log = Logger.getLogger(PlanMutexPreAndPostChainAction.class);
  public static final String PLAN_MUTEX_KEY = "custom.bamboo.planMutex.list";
  private PlanManager planManager;

  private static ConcurrentMap<String, String> runningPlans = new ConcurrentHashMap<String, String>();

  @Override
  public void execute(@NotNull Chain chain, @NotNull ChainExecution chainExecution) throws Exception {
    String id = chainExecution.getPlanResultKey().toString();
    Map<String, String> customConfig = chain.getBuildDefinition().getCustomConfiguration();
    String planMutexKey = customConfig.get(PLAN_MUTEX_KEY);

    if (planMutexKey != null && !planMutexKey.trim().isEmpty())
    {
      log.info("Starting to wait for mutex '" + planMutexKey + "' with id " + id);
      while (runningPlans.putIfAbsent(planMutexKey, id) != id) {
        Thread.sleep(1000);
      }

      // we have the mutex, check if we are still in running state
      if(chainExecution.isStopping() || chainExecution.isStopRequested())
      {
        log.info("Released mutex '" + planMutexKey + "' with id " + id + " since we are already stopped");
        runningPlans.remove(planMutexKey, id);
      }
      else
      {
        log.info("Locked mutex '" + planMutexKey + "' with id " + id);
      }
    }
  }

  @Override
  public void execute(Chain chain, ChainResultsSummary chainResultsSummary, ChainExecution chainExecution)
      throws InterruptedException, Exception {
    String id = chainExecution.getPlanResultKey().toString();
    Map<String, String> customConfig = chain.getBuildDefinition().getCustomConfiguration();
    String planMutexKey = customConfig.get(PLAN_MUTEX_KEY);

    if (planMutexKey != null && !planMutexKey.trim().isEmpty()) {
      if (runningPlans.remove(planMutexKey, id)) {
        log.info("Released mutex '" + planMutexKey + "' with id " + id);
      } else {
        log.error("Did not release mutex '" + planMutexKey + "' with id " + id + " since it was claimed by " + runningPlans.get(planMutexKey));
      }
    }
  }
}
