package com.adobe.aem.guides.wknd.core.filters;

import java.io.IOException;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.Servlet;

@Component(
    service = Servlet.class,
    property = {
        Constants.SERVICE_DESCRIPTION + "= User Info Servlet",
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=" + "/bin/userinfo",
        "sling.servlet.extensions=" + "json"
    }
)
public class UserInfoServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        
        logger.info("UserInfoServlet processing request: {}", request.getRequestURI());
        
        // Set response content type to JSON
        response.setContentType("application/json");
        
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        
        try {
            // Get the current user
            ResourceResolver resolver = request.getResourceResolver();
            String userId = resolver.getUserID();
            
            logger.info("Request user ID: {}", userId);
            
            // Add user ID to the JSON
            jsonBuilder.add("userId", userId);
            
            // Check if user is authenticated (not anonymous)
            if (userId != null && !userId.equals("anonymous")) {
                logger.info("Authenticated user detected: {}", userId);
                jsonBuilder.add("isAuthenticated", true);
                
                // Get more user details if available
                try {
                    logger.info("Attempting to get additional user details for {}", userId);
                    UserManager userManager = AccessControlUtil.getUserManager(resolver.adaptTo(javax.jcr.Session.class));
                    User user = (User) userManager.getAuthorizable(userId);
                    
                    if (user != null) {
                        logger.info("Found user object for {}", userId);
                        
                        // Add email to JSON (if available)
                        if (user.getProperty("profile/email") != null) {
                            String email = user.getProperty("profile/email")[0].getString();
                            jsonBuilder.add("email", email);
                            logger.info("Added email to response: {}", email);
                        } else {
                            jsonBuilder.add("email", "");
                            logger.info("No email found for user {}", userId);
                        }
                        
                        // Add country to JSON (if available)
                        if (user.getProperty("profile/country") != null) {
                            String country = user.getProperty("profile/country")[0].getString();
                            jsonBuilder.add("country", country);
                            logger.info("Added country to response: {}", country);
                        } else {
                            jsonBuilder.add("country", "");
                            logger.info("No country found for user {}", userId);
                        }
                    } else {
                        logger.info("User object could not be retrieved for ID: {}", userId);
                        jsonBuilder.add("error", "Could not retrieve user details");
                    }
                } catch (Exception e) {
                    logger.error("Error getting user details for {}: {}", userId, e.getMessage(), e);
                    jsonBuilder.add("error", "Error retrieving user details: " + e.getMessage());
                }
            } else {
                logger.info("Anonymous or unauthenticated user");
                jsonBuilder.add("isAuthenticated", false);
            }
        } catch (Exception e) {
            logger.error("Error in UserInfoServlet: {}", e.getMessage(), e);
            jsonBuilder.add("error", "Server error: " + e.getMessage());
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        
        // Write the JSON response
        response.getWriter().write(jsonBuilder.build().toString());
    }
}