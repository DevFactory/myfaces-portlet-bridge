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

package org.apache.myfaces.portlet.faces.util.config;

import java.io.InputStream;
import java.io.StringReader;

import java.net.URL;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.portlet.PortletContext;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

//TODO: This is probably better under the Bridge package as a package private utility
//TODO: This is probably better as a static utility class since it's never kept around.
public class FacesConfigurationProcessor
{

  private static final String FACES_CONFIG_METAINF_PATH = "META-INF/faces-config.xml";
  private static final String FACES_CONFIG_WEBINF_PATH = "/WEB-INF/faces-config.xml";
  private List<String> mExcludedAttributes = null;

  /**
   * <p>
   * When instantiated, the web.xml of the current application will be scanned looking for a
   * references to the <code>FacesServlet</code>. <code>isFacesServletPresent()</code> will
   * return the appropriate value based on the scan.
   * </p>
   * 
   * @param context
   *          the <code>ServletContext</code> for the application of interest
   */
  public FacesConfigurationProcessor(PortletContext context)
  {
    if (context != null)
    {
      scanForFacesMappings(context);
    }
  } // END WebXmlProcessor

  public List<String> getExcludedAttributes()
  {
    return mExcludedAttributes;
  } // END getFacesMappings

  /**
   * <p>
   * Parse the web.xml for the current application and scan for a FacesServlet entry, if found, set
   * the <code>facesServletPresent</code> property to true.
   * 
   * @param context
   *          the ServletContext instance for this application
   */
  private void scanForFacesMappings(PortletContext context)
  {

    SAXParserFactory factory = getSAXFactory();
    try
    {
      SAXParser parser = factory.newSAXParser();
      FacesConfigXmlHandler handler = null;
      
      // read each faces-config.xml and see if any contain the bridge extension
      // that defines some excluded attributes
      ClassLoader cl = getCurrentClassLoader(context);
      
      for (Enumeration<URL> items = cl.getResources(FACES_CONFIG_METAINF_PATH);
               items.hasMoreElements();) {
         
        URL nextElement = items.nextElement();
        
        if (handler == null) 
        {
          handler = new FacesConfigXmlHandler();
        }
        else
        {
          handler.reset();
        }
        parser.parse(nextElement.openStream(), handler);
      }
      
      // Now see if the web app has one in its WEB-INF
      InputStream configStream = context.getResourceAsStream(FACES_CONFIG_WEBINF_PATH);
      if (configStream != null)
      {
        parser.parse(configStream, handler);
      }
    }
    catch (Exception e)
    {
      // TODO add logging
      // Do nothing
      ;
    }

  } // END scanForFacesMappings
  
  private ClassLoader getCurrentClassLoader(Object fallbackClass) {
      ClassLoader loader =
          Thread.currentThread().getContextClassLoader();
      if (loader == null) {
          loader = fallbackClass.getClass().getClassLoader();
      }
      return loader;
  }

  /**
   * <p>
   * Return a <code>SAXParserFactory</code> instance that is non-validating and is namespace
   * aware.
   * </p>
   * 
   * @return configured <code>SAXParserFactory</code>
   */
  private SAXParserFactory getSAXFactory()
  {

    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setValidating(false);
    factory.setNamespaceAware(true);
    return factory;

  } // END getConfiguredFactory

  /**
   * <p>
   * A simple SAX handler to process the elements of interested within a web application's
   * deployment descriptor.
   * </p>
   */
  private class FacesConfigXmlHandler extends DefaultHandler
  {

    private static final String APPLICATION_ELEMENT          = "application";
    private static final String APP_EXTENSION_ELEMENT     = "application-extension";
    private static final String EXCLUDED_ATTRIBUTES_ELEMENT    = "excluded-attributes";
    private static final String EXCLUDED_ATTRIBUTE_ELEMENT  = "excluded-attribute";

    private boolean             mInApplicationElement        = false;
    private boolean             mInApplicationExtensionElement    = false;
    private boolean             mInExcludedAttributesElement   = false;
   
    private StringBuilder       mContent;


    public void reset()
    {
      mInApplicationElement = mInApplicationExtensionElement =
        mInExcludedAttributesElement = false;
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException
    {

      return new InputSource(new StringReader(""));

    } // END resolveEntity

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
                                                                                               throws SAXException
    {

      if (APPLICATION_ELEMENT.equals(localName))
      {
        mInApplicationElement = true;
      }
      else if (APP_EXTENSION_ELEMENT.equals(localName))
      {
        mInApplicationExtensionElement = true;
      }
      else if (EXCLUDED_ATTRIBUTES_ELEMENT.equals(localName))
      {
          mInExcludedAttributesElement = true;
      }
      else if (EXCLUDED_ATTRIBUTE_ELEMENT.equals(localName))
      {
        if (mInApplicationElement && mInApplicationExtensionElement &&
          mInExcludedAttributesElement)
        {
          mContent = new StringBuilder();
        }
      }
    } // END startElement

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException
    {

      if (mContent != null)
      {
        mContent.append(ch, start, length);
      }

    } // END characters

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException
    {

      if (APPLICATION_ELEMENT.equals(localName))
      {
        mInApplicationElement = false;
      }
      else if (APP_EXTENSION_ELEMENT.equals(localName))
      {
        mInApplicationExtensionElement = false;
      }
      else if (EXCLUDED_ATTRIBUTES_ELEMENT.equals(localName))
      {
          mInExcludedAttributesElement = false;
      }
      else if (EXCLUDED_ATTRIBUTE_ELEMENT.equals(localName) && mContent != null
        && mContent.length() > 0)
      {
        // add mContent to the attrs list
        String excludedAttribute = mContent.toString().trim();
        
        if (mExcludedAttributes == null)
        {
          mExcludedAttributes = (List<String> )new ArrayList(5);
        }
        
        if (!mExcludedAttributes.contains(excludedAttribute))
        {
          mExcludedAttributes.add(excludedAttribute);
        }
      }

      mContent = null;

    } // END endElement

  } // END FacesConfigXmlHandler

}
