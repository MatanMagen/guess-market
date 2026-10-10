package guessmarket.server;

import guessmarket.engine.api.Engine;
import guessmarket.engine.impl.GuessMarketEngine;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Creates the one engine and the one chat room every request shares, when Tomcat deploys the
 * application. This is the only place the concrete engine class is named.
 * <p>
 * Nothing is persisted: when the server goes down, every user, event and chat line goes with it.
 */
@WebListener
public class ServerSetup implements ServletContextListener {

    private static final String ENGINE = "guessmarket.engine";
    private static final String CHAT = "guessmarket.chat";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setAttribute(ENGINE, new GuessMarketEngine());
        event.getServletContext().setAttribute(CHAT, new ChatRoom());
    }

    static Engine engine(ServletContext context) {
        return (Engine) context.getAttribute(ENGINE);
    }

    static ChatRoom chat(ServletContext context) {
        return (ChatRoom) context.getAttribute(CHAT);
    }
}
