/* Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.myfaces.portlet.faces.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELContextEvent;
import javax.el.ELContextListener;

import javax.faces.FacesException;
import javax.faces.FactoryFinder;
import javax.faces.application.Application;
import javax.faces.application.ApplicationFactory;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseStream;
import javax.faces.context.ResponseWriter;
import javax.faces.lifecycle.Lifecycle;
import javax.faces.render.RenderKit;
import javax.faces.render.RenderKitFactory;
import javax.portlet.PortletResponse;
import javax.portlet.faces.Bridge;
import javax.portlet.faces.annotation.PortletNamingContainer;

import org.apache.myfaces.portlet.faces.el.PortletELContextImpl;

/**
 * Implementation of <code>FacesContext</code> for portlet environment
 */
public class PortletFacesContextImpl extends FacesContext
{
  private Application           mApplication;
  private RenderKitFactory      mRenderKitFactory;
  private ExternalContext       mExternalContext;
  private ResponseStream        mResponseStream   = null;
  private ResponseWriter        mResponseWriter   = null;
  private UIViewRoot            mViewRoot;
  private boolean               mRenderResponse   = false;
  private boolean               mResponseComplete = false;
  private FacesMessage.Severity mMaximumSeverity  = FacesMessage.SEVERITY_INFO;
  private ELContext             mElContext        = null;
  private Map<String, List<FacesMessage>> mMessages = new HashMap<String, List<FacesMessage>>();

  public PortletFacesContextImpl(ExternalContext externalContext, Lifecycle lifecycle)
                                                                                      throws FacesException
  {
    mApplication = ((ApplicationFactory) FactoryFinder
                                                      .getFactory(FactoryFinder.APPLICATION_FACTORY))
                                                                                                     .getApplication();
    mRenderKitFactory = (RenderKitFactory) FactoryFinder
                                                        .getFactory(FactoryFinder.RENDER_KIT_FACTORY);
    mExternalContext = externalContext;

    FacesContext.setCurrentInstance(this);
  }

  // Start of JSF 1.2 API

  /**
   * <p>
   * Return the <code>ELContext</code> instance for this <code>FacesContext</code> instance.
   * This <code>ELContext</code> instance has the same lifetime and scope as the
   * <code>FacesContext</code> instance with which it is associated, and may be created lazily the
   * first time this method is called for a given <code>FacesContext</code> instance. Upon
   * creation of the ELContext instance, the implementation must take the following action:
   * </p>
   * 
   * <ul>
   * 
   * <li>
   * <p>
   * Call the {@link ELContext#putContext} method on the instance, passing in
   * <code>FacesContext.class</code> and the <code>this</code> reference for the
   * <code>FacesContext</code> instance itself.
   * </p>
   * </li>
   * 
   * <li>
   * <p>
   * If the <code>Collection</code> returned by {@link
   * javax.faces.application.Application#getELContextListeners} is non-empty, create an instance of
   * {@link javax.el.ELContextEvent} and pass it to each {@link javax.el.ELContextListener} instance
   * in the <code>Collection</code> by calling the {@link
   * javax.el.ELContextListener#contextCreated} method.
   * </p>
   * </li>
   * 
   * </ul>
   * 
   * <p>
   * The default implementation throws <code>UnsupportedOperationException</code> and is provided
   * for the sole purpose of not breaking existing applications that extend this class.
   * </p>
   * 
   * @throws IllegalStateException
   *           if this method is called after this instance has been released
   * 
   * @since 1.2
   */
  @Override
  public ELContext getELContext()
  {
    if (mElContext == null)
    {
      Application app = getApplication();
      mElContext = new PortletELContextImpl(app.getELResolver());
      mElContext.putContext(FacesContext.class, this);
      UIViewRoot root = getViewRoot();
      if (null != root)
      {
        mElContext.setLocale(root.getLocale());
      }
      
      // Now notify any listeners that we have created this context
      ELContextListener[] listeners = app.getELContextListeners();
      if (listeners.length > 0)
      {
        ELContextEvent event = new ELContextEvent(mElContext);
        for (ELContextListener listener:listeners)
        {
          listener.contextCreated(event);
        }
      }
    }
    return mElContext;
  }

  // End of JSF 1.2 API

  @Override
  public ExternalContext getExternalContext()
  {
    return mExternalContext;
  }

  @Override
  public FacesMessage.Severity getMaximumSeverity()
  {
    return mMaximumSeverity;
  }

  @Override
  public Iterator<FacesMessage> getMessages()
  {
    List<FacesMessage> results = new ArrayList<FacesMessage>();
    for (List<FacesMessage> messages : mMessages.values())
    {
      results.addAll(messages);
    }
    
    return results.iterator();
  }

  @Override
  public Application getApplication()
  {
    return mApplication;
  }

  @Override
  public Iterator<String> getClientIdsWithMessages()
  {
    return mMessages.keySet().iterator();
  }

  @Override
  public Iterator<FacesMessage> getMessages(String clientId)
  {
    List<FacesMessage> list = mMessages.get(clientId);
    if (list == null)
    {
      list = Collections.emptyList();
    }
    
    return list.iterator();
  }

  @Override
  public RenderKit getRenderKit()
  {
    if (getViewRoot() == null)
    {
      return null;
    }

    String renderKitId = getViewRoot().getRenderKitId();

    if (renderKitId == null)
    {
      return null;
    }

    return mRenderKitFactory.getRenderKit(this, renderKitId);

  }

  @Override
  public boolean getRenderResponse()
  {
    return mRenderResponse;
  }

  @Override
  public boolean getResponseComplete()
  {
    return mResponseComplete;
  }

  @Override
  public void setResponseStream(ResponseStream responseStream)
  {
    if (responseStream == null)
    {
      throw new NullPointerException("setResponseStream(null)");
    }
    mResponseStream = responseStream;
  }

  @Override
  public ResponseStream getResponseStream()
  {
    return mResponseStream;
  }

  @Override
  public void setResponseWriter(ResponseWriter responseWriter)
  {
    if (responseWriter == null)
    {
      throw new NullPointerException("setResponseWriter(null)");
    }
    mResponseWriter = responseWriter;
  }

  @Override
  public ResponseWriter getResponseWriter()
  {
    return mResponseWriter;
  }

  @Override
  public void setViewRoot(UIViewRoot viewRoot)
  {
    if (viewRoot == null)
    {
      throw new NullPointerException("setViewRoot(null)");
    }

    mViewRoot = viewRoot;

    // if ViewRoot annotated with PortletNamingContainer
    // it supports portlet namespacing content -- mark
    // response so consumers can detect
    if (mViewRoot.getClass().getAnnotation(PortletNamingContainer.class) != null)
    {
      try
      {
        PortletResponse pr = (PortletResponse) mExternalContext.getResponse();
        pr.addProperty(Bridge.PORTLET_ISNAMESPACED_PROPERTY, "true");
      }
      catch (Exception e)
      {
        // TODO: log message
        ; // do nothing -- just forge ahead
      }
    }
  }

  @Override
  public UIViewRoot getViewRoot()
  {
    return mViewRoot;
  }

  @Override
  public void addMessage(String clientId, FacesMessage message)
  {
    if (message == null)
    {
      throw new NullPointerException();
    }
    
    List<FacesMessage> list = mMessages.get(clientId);
    if (list == null)
    {
      list = new ArrayList<FacesMessage>(2); // default capacity of 10 is an overkill
      mMessages.put(clientId, list);
    }
    list.add(message);

    // -= Simon Lessard =-
    // FIXME: This is wrong, FacesMessage.setSeverity can be called later making
    //        this cached result potentially incoherent.
    FacesMessage.Severity severity = message.getSeverity();
    if (severity != null && severity.compareTo(mMaximumSeverity) > 0)
    {
      mMaximumSeverity = severity;
    }
  }

  @Override
  public void release()
  {
    if (mExternalContext != null && mExternalContext instanceof PortletExternalContextImpl)
    {
      ((PortletExternalContextImpl) mExternalContext).release();
      mExternalContext = null;
    }

    mApplication = null;
    mResponseStream = null;
    mResponseWriter = null;
    mViewRoot = null;
    mElContext = null;
    FacesContext.setCurrentInstance(null);
  }

  @Override
  public void renderResponse()
  {
    mRenderResponse = true;
  }

  @Override
  public void responseComplete()
  {
    mResponseComplete = true;
  }
}
