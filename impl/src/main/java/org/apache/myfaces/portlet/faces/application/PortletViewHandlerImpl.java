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

package org.apache.myfaces.portlet.faces.application;

import java.io.IOException;
import java.io.Writer;

import javax.faces.FacesException;
import javax.faces.FactoryFinder;
import javax.faces.application.StateManager;
import javax.faces.application.ViewHandler;
import javax.faces.application.ViewHandlerWrapper;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.render.RenderKit;
import javax.faces.render.RenderKitFactory;
import javax.portlet.RenderResponse;
import javax.portlet.faces.Bridge;
import javax.portlet.faces.BridgeUtil;
import javax.portlet.faces.component.PortletNamingContainerUIViewRoot;

import org.apache.myfaces.portlet.faces.context.PortletExternalContextImpl;

/**
 * View handler implementation for JSF portlet bridge.
 * 
 * The only method we override here is getActionURL().
 * 
 * TODO JSF 1.2 note: JSF 1.2 RI implements ViewHandler.renderView() differently in order to handle
 * emitting non-JSF markup that follows the JSF tags after the JSF renders correctly. Unfortunately,
 * the RI handles this by introducing several servlet dependencies. Currently, the bridge handles
 * this by overriding the renderView() and ignoring (not interleafing) the non-JSF markup - see HACK
 * below
 */
public class PortletViewHandlerImpl extends ViewHandlerWrapper
{

  // the ViewHandler to delegate to
  private ViewHandler mDelegate;

  public PortletViewHandlerImpl(ViewHandler handler)
  {
    mDelegate = handler;
  }
  
  protected ViewHandler getWrapped()
  {
    return mDelegate;
  }


  @Override
  public UIViewRoot createView(FacesContext facesContext, String viewId)
  {

    UIViewRoot viewRoot = mDelegate.createView(facesContext, viewId);
 
    if (viewRoot.getClass() != UIViewRoot.class)
    {
      return viewRoot;
    }
    else
    {
      return new PortletNamingContainerUIViewRoot(viewRoot);
    }

  }



  @Override
  public void renderView(FacesContext context, UIViewRoot viewToRender) throws IOException,
                                                                       FacesException
  {
    // Get the renderPolicy from the requestScope
    Bridge.BridgeRenderPolicy renderPolicy = (Bridge.BridgeRenderPolicy) context
                                                                                .getExternalContext()
                                                                                .getRequestMap()
                                                                                .get(
                                                                                     PortletExternalContextImpl.RENDER_POLICY_ATTRIBUTE);

    if (renderPolicy == null)
    {
      renderPolicy = Bridge.BridgeRenderPolicy.valueOf("DEFAULT");
    }

    if (!BridgeUtil.isPortletRequest()
        || renderPolicy == Bridge.BridgeRenderPolicy.ALWAYS_DELEGATE)
    {
      mDelegate.renderView(context, viewToRender);
      return;
    }
    else if (renderPolicy == Bridge.BridgeRenderPolicy.DEFAULT)
    {
      try
      {
        mDelegate.renderView(context, viewToRender);
        return;
      }
      catch (Throwable t)
      {
        // catch all throws and swallow -- falling through to our own
        // render
      }
    }

    // suppress rendering if "rendered" property on the component is
    // false
    if (!viewToRender.isRendered())
    {
      return;
    }

    ExternalContext extContext = context.getExternalContext();
    RenderResponse renderResponse = (RenderResponse) extContext.getResponse();

    try
    {

      // set request attribute indicating we can deal with content
      // that is supposed to be delayed until after JSF tree is ouput.
      extContext.getRequestMap().put(Bridge.RENDER_CONTENT_AFTER_VIEW, Boolean.TRUE);
      // TODO JSF 1.2 - executePageToBuildView() creates
      // ViewHandlerResponseWrapper
      // to handle error page and text that exists after the <f:view> tag
      // among other things which have lots of servlet dependencies -
      // we're skipping this for now for portlet
      
      
      // Bridge has had to set this attribute so  Faces RI will skip servlet dependent
      // code when mapping from request paths to viewIds -- however we need to remove it
      // as it screws up the dispatch
      extContext.getRequestMap().remove("javax.servlet.include.servlet_path");
      extContext.dispatch(viewToRender.getViewId());
      /*
       * if (executePageToBuildView(context, viewToRender)) { response.flushBuffer(); return; }
       */
    }
    catch (IOException e)
    {
      throw new FacesException(e);
    }

    // set up the ResponseWriter
    RenderKitFactory renderFactory = (RenderKitFactory) FactoryFinder
                                                                     .getFactory(FactoryFinder.RENDER_KIT_FACTORY);
    RenderKit renderKit = renderFactory.getRenderKit(context, viewToRender.getRenderKitId());

    ResponseWriter oldWriter = context.getResponseWriter();
    StringBuilderWriter strWriter = new StringBuilderWriter(context, 4096);
    ResponseWriter newWriter;
    if (null != oldWriter)
    {
      newWriter = oldWriter.cloneWithWriter(strWriter);
    }
    else
    {
      newWriter = renderKit.createResponseWriter(strWriter, null,
                                                 renderResponse.getCharacterEncoding());
    }
    context.setResponseWriter(newWriter);

    newWriter.startDocument();

    doRenderView(context, viewToRender);

    newWriter.endDocument();

    // replace markers in the body content and write it to response.

    ResponseWriter responseWriter;
    if (null != oldWriter)
    {
      responseWriter = oldWriter.cloneWithWriter(renderResponse.getWriter());
    }
    else
    {
      responseWriter = newWriter.cloneWithWriter(renderResponse.getWriter());
    }
    context.setResponseWriter(responseWriter);

    strWriter.write(responseWriter);

    if (null != oldWriter)
    {
      context.setResponseWriter(oldWriter);
    }

    Object content = extContext.getRequestMap().get(Bridge.AFTER_VIEW_CONTENT);
    if (content != null)
    {
      if (content instanceof char[])
      {
        renderResponse.getWriter().write(new String((byte[]) content));
      }
      else if (content instanceof byte[])
      {
        renderResponse.getWriter().write(new String((char[]) content));
      }
      else
      {
        throw new IOException("PortletViewHandlerImpl: invalid" + "AFTER_VIEW_CONTENT buffer type");
      }
    }
    renderResponse.flushBuffer();
  }

  /**
   * <p>
   * This is a separate method to account for handling the content after the view tag.
   * </p>
   * 
   * <p>
   * Create a new ResponseWriter around this response's Writer. Set it into the FacesContext, saving
   * the old one aside.
   * </p>
   * 
   * <p>
   * call encodeBegin(), encodeChildren(), encodeEnd() on the argument <code>UIViewRoot</code>.
   * </p>
   * 
   * <p>
   * Restore the old ResponseWriter into the FacesContext.
   * </p>
   * 
   * <p>
   * Write out the after view content to the response's writer.
   * </p>
   * 
   * <p>
   * Flush the response buffer, and remove the after view content from the request scope.
   * </p>
   * 
   * @param context
   *          the <code>FacesContext</code> for the current request
   * @param viewToRender
   *          the view to render
   * @throws IOException
   *           if an error occurs rendering the view to the client
   */
  private void doRenderView(FacesContext context, UIViewRoot viewToRender) throws IOException,
                                                                          FacesException
  {
    viewToRender.encodeAll(context);
  }
  

  private static final class StringBuilderWriter extends Writer
  {
    private StringBuilder       mBuilder;
    private FacesContext        mContext;

    // TODO: These bridge needs to use it's own constants here. This will
    // confine
    // us to only work with the R.I.
    private static final String SAVESTATE_FIELD_MARKER = "~com.sun.faces.saveStateFieldMarker~";

    public StringBuilderWriter(FacesContext context, int initialCapacity)
    {
      if (initialCapacity < 0)
      {
        throw new IllegalArgumentException();
      }
      mBuilder = new StringBuilder(initialCapacity);
      mContext = context;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException
    {
      if (off < 0 || off > cbuf.length || len < 0 || off + len > cbuf.length || off + len < 0)
      {
        throw new IndexOutOfBoundsException();
      }
      else if (len == 0)
      {
        return;
      }
      mBuilder.append(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException
    {
    }

    @Override
    public void close() throws IOException
    {
    }

    /**
     * Write a string.
     * 
     * @param str
     *          String to be written
     */
    @Override
    public void write(String str)
    {
      mBuilder.append(str);
    }

    @Override
    public void write(String str, int off, int len)
    {
      write(str.substring(off, off + len));
    }

    public StringBuilder getBuffer()
    {
      return mBuilder;
    }

    @Override
    public String toString()
    {
      return mBuilder.toString();
    }

    public void write(Writer writer) throws IOException
    {
      // TODO: Buffer?
      StateManager stateManager = mContext.getApplication().getStateManager();
      Object stateToWrite = stateManager.saveView(mContext);
      int markLen = SAVESTATE_FIELD_MARKER.length();
      int pos = 0;
      int tildeIdx = mBuilder.indexOf(SAVESTATE_FIELD_MARKER);
      while (tildeIdx > 0)
      {
        writer.write(mBuilder.substring(pos, (tildeIdx - pos)));
        stateManager.writeState(mContext, stateToWrite);
        pos += tildeIdx + markLen;
        tildeIdx = mBuilder.indexOf(SAVESTATE_FIELD_MARKER, pos);
      }
      writer.write(mBuilder.substring(pos));
    }
  }

  // END TODO HACK JSF 1.2
}
