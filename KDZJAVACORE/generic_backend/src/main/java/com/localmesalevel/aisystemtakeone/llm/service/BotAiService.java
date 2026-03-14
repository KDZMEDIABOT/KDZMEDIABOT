package com.localmesalevel.aisystemtakeone.llm.service;

import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.transaction.Transactional;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.ejb.HibernateEntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.provider.HibernateUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.orm.jpa.vendor.HibernateJpaSessionFactoryBean;
import org.springframework.stereotype.Service;

import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import com.localmesalevel.aisystemtakeone.websocket.AiRequestCallback;
import com.localmesalevel.aisystemtakeone.websocket.BotWebSocketHandler;

/**
 * Service that handles AI requests from the bot.
 * Implements AiRequestCallback to receive requests from WebSocket.
 */
@Service
@Configuration
public class BotAiService implements AiRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(BotAiService.class);

    private final BotWebSocketHandler webSocketHandler;
    private final LlmService llmService;
    private final UserAccountRepository userAccountRepository;
    private final LlmConnectionFactory llmConnectionFactory;

    @Autowired
	private EntityManager entityManager;

    public BotAiService(@Lazy BotWebSocketHandler webSocketHandler,
                        LlmService llmService,
                        UserAccountRepository userAccountRepository,
                        LlmConnectionFactory llmConnectionFactory) {
        this.webSocketHandler = webSocketHandler;
        this.llmService = llmService;
        this.userAccountRepository = userAccountRepository;
        this.llmConnectionFactory = llmConnectionFactory;
    }

    @PostConstruct
    public void init() {
        webSocketHandler.setAiRequestCallback(this);
        logger.info("BotAiService initialized and registered callback");
    }

    @Override
    public void onAiRequest(
            String requestId,
            String userId,
            String channel,
            String platform,
            String systemPrompt,
            String userQuery,
            Consumer<String> onSuccess,
            Consumer<String> onError) {

        logger.debug("Processing AI request {} from {} on {}", requestId, userId, platform);

        // Get KDZMEDIABOT user's LLM connection
        LlmConnection llmConnection = getKdmediabotConnection();
        if (llmConnection == null) {
            logger.error("Failed to get LLM connection for KDZMEDIABOT user");
            onError.accept("Error: LLM connection not configured for bot");
            return;
        }

        // Process in async way using CompletableFuture
        llmService.processBotQuery(llmConnection, systemPrompt, userQuery)
                .thenAccept(response -> {
                    logger.info("AI request {} completed, response length: {}",
                            requestId, response.length());
                    onSuccess.accept(response);
                })
                .exceptionally(throwable -> {
                    logger.error("AI request {} failed", requestId, throwable);
                    onError.accept("Error: " + throwable.getMessage());
                    return null;
                });
    }
        
    /**
     * Get LLM connection for user "KDZMEDIABOT".
     * Extracts credentials from the user's current_llm_endpoint.
     */
    public LlmConnection getKdmediabotConnection() {
    	Session session = entityManager.unwrap(Session.class);
    	Transaction t = session.beginTransaction();
    	try {
    		LlmEndpointCredentials credentialsOpt = 
    				(LlmEndpointCredentials) entityManager.createQuery(
    						"select c from LlmEndpointCredentials c, UserAccount u where u.currentLlmEndpoint = c and u.username = 'KDZMEDIABOT'", 
    						LlmEndpointCredentials.class).getSingleResult();
            if (credentialsOpt == null) {
                logger.error("credentialsOpt.isEmpty()");
                return null;
            }
//
//            UserAccount botUser = botUserOpt.get();
//            LlmEndpointCredentials credentials = botUser.getCurrentLlmEndpoint();
//            //Hibernate.initialize(credentials);
//            if (credentials) {
//                logger.error("User KDZMEDIABOT has no current LLM endpoint configured");
//                return null;
//            }
//
            LlmConnection llmConnection = llmConnectionFactory.create(credentialsOpt);
            t.commit();
            return llmConnection;
        } catch (Throwable e) {
            t.rollback();
            logger.error("Failed to create LLM connection for KDZMEDIABOT err2", e);
            return null;
        } finally {
        	try {
        		session.close();
        	} catch(Throwable tr) {
        		logger.error("", tr);
        	}
        }
    }
}
