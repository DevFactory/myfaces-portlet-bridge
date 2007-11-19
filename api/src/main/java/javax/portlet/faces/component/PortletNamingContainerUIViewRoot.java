/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */
package javax.portlet.faces.component;

import java.io.Serializable;

import javax.faces.context.FacesContext;
import javax.faces.component.NamingContainer;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;

/**
 * Bridge ViewRoot that implements NamingContainer which uses the ExternalContext.encodeNamespace to
 * introduce the consumer namespace into tree components.
 */
public class PortletNamingContainerUIViewRoot extends UIViewRoot implements PortletNamingContainer,
    Serializable
{

  //TODO: This should be regenerated each time this is modified.  Can this be added to maven?
  private static final long   serialVersionUID = -4524288011655837711L;
  private static final String SEPARATOR        = (new Character(NamingContainer.SEPARATOR_CHAR))
                                                                                                .toString();

  public PortletNamingContainerUIViewRoot()
  {
    super();
  }

  public PortletNamingContainerUIViewRoot(UIViewRoot viewRootToReplace)
  {
    super();
    setViewId(viewRootToReplace.getViewId());
    setLocale(viewRootToReplace.getLocale());
    setRenderKitId(viewRootToReplace.getRenderKitId());
  }

  // Implement the method that satisfies NamingContainer

  public static String getContainerClientId(FacesContext context, String additionalId)
  {
    ExternalContext ec = context.getExternalContext();
    String namespace = ec.encodeNamespace(SEPARATOR);

    /*
     * In servlet world encodeNamespace does nothing -- so if we get back what we sent in then do
     * not perturn the NamingContainer Id
     */
    if (namespace.length() > 1)
    {
      if (additionalId != null)
      {
        return namespace + additionalId;
      }
      else
      {
        return namespace;
      }
    }
    else
    {
      return null;
    }
  }

  // Implement the method that satisfies NamingContainer

  @Override
  public String getContainerClientId(FacesContext context)
  {
    return PortletNamingContainerUIViewRoot
                                           .getContainerClientId(
                                                                 context,
                                                                 super
                                                                      .getContainerClientId(context));
  }

}
