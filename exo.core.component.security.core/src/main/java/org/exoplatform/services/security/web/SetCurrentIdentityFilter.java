/*
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.services.security.web;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.web.AbstractFilter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.ConversationRegistry;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.services.security.StateKey;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Created by The eXo Platform SAS .
 *
 * @author <a href="mailto:gennady.azarenkov@exoplatform.com">Gennady
 *         Azarenkov</a>
 * @version $Id: SetCurrentIdentityFilter.java 7163 2006-07-19 07:30:39Z peterit
 *          $
 */
public class SetCurrentIdentityFilter extends AbstractFilter
{

   private boolean restoreIdentity;

   /**
    * Logger.
    */
   private static Log log = ExoLogger.getLogger("exo.core.component.security.core.SetCurrentIdentityFilter");

   /**
    * {@inheritDoc}
    */
   @Override
   protected void afterInit(FilterConfig config) throws ServletException
   {
      super.afterInit(config);
      restoreIdentity = Boolean.parseBoolean(config.getInitParameter("restoreIdentity"));
   }

   /**
    * Set current {@link ConversationState}, if it is not registered yet then
    * create new one and register in {@link ConversationRegistry}. {@inheritDoc}
    */
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
      ServletException
   {

      HttpServletRequest httpRequest = (HttpServletRequest)request;
      ExoContainer container = getContainer();

      try
      {
         ExoContainerContext.setCurrentContainer(container);
         ConversationState state = getCurrentState(container, httpRequest);
         // NOTE may be set as null
         ConversationState.setCurrent(state);
         if (state != null && log.isDebugEnabled())
            log.debug(">>> Memberships " + state.getIdentity().getMemberships());
         chain.doFilter(request, response);
      }
      finally
      {
         try
         {
            ConversationState.setCurrent(null);
         }
         catch (Exception e)
         {
            log.warn("An error occured while cleaning the ThreadLocal", e);
         }
         try
         {
            ExoContainerContext.setCurrentContainer(null);
         }
         catch (Exception e)
         {
            log.warn("An error occured while cleaning the ThreadLocal", e);
         }
      }
   }

   /**
    * Gives the current state
    */
   private ConversationState getCurrentState(ExoContainer container, HttpServletRequest httpRequest)
   {
      ConversationRegistry conversationRegistry =
         (ConversationRegistry)container.getComponentInstanceOfType(ConversationRegistry.class);

      IdentityRegistry identityRegistry =
         (IdentityRegistry)container.getComponentInstanceOfType(IdentityRegistry.class);

      ConversationState state = null;
      String userId = httpRequest.getRemoteUser();

      // only if user authenticated, otherwise there is no reason to do anythings
      if (userId != null)
      {
         HttpSession httpSession = httpRequest.getSession();
         StateKey stateKey = new HttpSessionStateKey(httpSession);

         if (log.isDebugEnabled())
         {
            log.debug("Looking for Conversation State " + httpSession.getId());
         }

         state = conversationRegistry.getState(stateKey);

         if (state == null)
         {
            if (log.isDebugEnabled())
            {
               log.debug("Conversation State not found, try create new one.");
            }

            Identity identity = identityRegistry.getIdentity(userId);
            if (identity != null)
            {
               state = new ConversationState(identity);
               // Keep subject as attribute in ConversationState.
               // TODO remove this, do not need it any more.
               state.setAttribute(ConversationState.SUBJECT, identity.getSubject());
            }
            else
            {
               if (restoreIdentity)
               {
                  if (log.isDebugEnabled())
                  {
                     log.debug("Not found identity for " + userId + " try to restore it. ");
                  }

                  Authenticator authenticator =
                     (Authenticator)container.getComponentInstanceOfType(Authenticator.class);
                  try
                  {
                     identity = authenticator.createIdentity(userId);
                     identityRegistry.register(identity);
                  }
                  catch (Exception e)
                  {
                     log.error("Unable restore identity. " + e.getMessage(), e);
                  }

                  if (identity != null)
                  {
                     state = new ConversationState(identity);
                  }
               }
               else
               {
                  log.error("Not found identity in IdentityRegistry for user " + userId + ", check Login Module.");
               }
            }

            if (state != null)
            {
               conversationRegistry.register(stateKey, state);
               if (log.isDebugEnabled())
               {
                  log.debug("Register Conversation state " + httpSession.getId());
               }
            }
         }
      }
      return state;
   }

   /**
    * {@inheritDoc}
    */
   public void destroy()
   {
      // nothing to do.
   }

}
