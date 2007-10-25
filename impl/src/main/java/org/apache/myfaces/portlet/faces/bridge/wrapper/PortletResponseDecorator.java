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

package org.apache.myfaces.portlet.faces.bridge.wrapper;

import javax.portlet.PortletResponse;

public class PortletResponseDecorator
    implements PortletResponse
{
    protected PortletResponse mResponse = null;


    public PortletResponseDecorator(PortletResponse response)
        throws IllegalArgumentException
    {
        if (response == null)
            throw new IllegalArgumentException();
        mResponse = response;
    }

    public PortletResponse getParent()
    {
        return mResponse;
    }

    public PortletResponse getRoot()
    {
        PortletResponse root = mResponse;
        while (root instanceof PortletResponseDecorator)
        {
            root = ((PortletResponseDecorator) root).getParent();
        }
        return root;
    }

    /**
     * Adds a String property to an existing key to be returned to the portal.
     * <p>
     * This method allows response properties to have multiple values.
     * <p>
     * Properties can be used by portlets to provide vendor specific 
     * information to the portal.
     *
     * @param  key    the key of the property to be returned to the portal
     * @param  value  the value of the property to be returned to the portal
     *
     * @exception  java.lang.IllegalArgumentException 
     *                            if key is <code>null</code>.
     */
    public

    void addProperty(String key, String value)
    {
        mResponse.addProperty(key, value);
    }


    /**
     * Sets a String property to be returned to the portal.
     * <p>
     * Properties can be used by portlets to provide vendor specific 
     * information to the portal.
     * <p>
     * This method resets all properties previously added with the same key.
     *
     * @param  key    the key of the property to be returned to the portal
     * @param  value  the value of the property to be returned to the portal
     *
     * @exception  java.lang.IllegalArgumentException 
     *                            if key is <code>null</code>.
     */
    public

    void setProperty(String key, String value)
    {
        mResponse.setProperty(key, value);
    }


    /**
     * Returns the encoded URL of the resource, like servlets,
     * JSPs, images and other static files, at the given path.
     * <p>
     * Some portal/portlet-container implementation may require 
     * those URLs to contain implementation specific data encoded
     * in it. Because of that, portlets should use this method to 
     * create such URLs.
     * <p>
     * The <code>encodeURL</code> method may include the session ID 
     * and other portal/portlet-container specific information into the URL. 
     * If encoding is not needed, it returns the URL unchanged. 
     *
     * @param   path
     *          the URI path to the resource. This must be either
     *          an absolute URL (e.g. 
     *          <code>http://my.co/myportal/mywebap/myfolder/myresource.gif</code>)
     *          or a full path URI (e.g. <code>/myportal/mywebap/myfolder/myresource.gif</code>).
     *
     * @exception  java.lang.IllegalArgumentException 
     *                            if path doesn't have a leading slash or is not an absolute URL
     * 
     * @return   the encoded resource URL as string
     */
    public

    String encodeURL(String path)
    {
        return mResponse.encodeURL(path);
    }


}
