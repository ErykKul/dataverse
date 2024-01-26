package edu.harvard.iq.dataverse.cache;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.GuestUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.util.SystemConfig;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;

import java.util.logging.Logger;
import java.util.Map;

@Singleton
@Startup
public class CacheFactoryBean implements java.io.Serializable {
    private static final Logger logger = Logger.getLogger(CacheFactoryBean.class.getCanonicalName());
    private static HazelcastInstance hazelcastInstance = null;
    private static Map<String, String> rateLimitCache;
    @EJB
    SystemConfig systemConfig;

    public final static String RATE_LIMIT_CACHE = "rateLimitCache";

    @PostConstruct
    public void init() {
        if (hazelcastInstance == null) {
            Config hazelcastConfig = new Config();
            hazelcastConfig.setClusterName("dataverse");
            hazelcastInstance = Hazelcast.newHazelcastInstance(hazelcastConfig);
            rateLimitCache = hazelcastInstance.getMap(RATE_LIMIT_CACHE);
        }
    }
    @Override
    protected void finalize() throws Throwable {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
        super.finalize();
    }

    /**
     * Check if user can make this call or if they are rate limited
     * @param user
     * @param action
     * @return true if user is superuser or rate not limited
     */
    public boolean checkRate(User user, String action) {
        if (user != null && user.isSuperuser()) {
            return true;
        };
        StringBuffer id = new StringBuffer();
        id.append(user != null ? user.getIdentifier() : GuestUser.get().getIdentifier());
        if (action != null) {
            id.append(":").append(action);
        }

        // get the capacity, i.e. calls per hour, from config
        int capacity = (user instanceof AuthenticatedUser) ?
                RateLimitUtil.getCapacityByTierAndAction(systemConfig, ((AuthenticatedUser) user).getRateLimitTier(), action) :
                RateLimitUtil.getCapacityByTierAndAction(systemConfig, 0, action);
        return (!RateLimitUtil.rateLimited(rateLimitCache, id.toString(), capacity));
    }

    public long getCacheSize(String cacheName) {
        long cacheSize = 0;
        switch (cacheName) {
            case RATE_LIMIT_CACHE:
                cacheSize = rateLimitCache.size();
                break;
            default:
                break;
        }
        return cacheSize;
    }
    public Object getCacheValue(String cacheName, String key) {
        Object cacheValue = null;
        switch (cacheName) {
            case RATE_LIMIT_CACHE:
                cacheValue = rateLimitCache.containsKey(key) ? rateLimitCache.get(key) : "";
                break;
            default:
                break;
        }
        return cacheValue;
    }
    public void setCacheValue(String cacheName, String key, Object value) {
        switch (cacheName) {
            case RATE_LIMIT_CACHE:
                rateLimitCache.put(key, (String) value);
                break;
            default:
                break;
        }
    }
}
