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

import java.io.StringReader;

import java.util.Vector;

import javax.portlet.PortletContext;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class WebConfigurationProcessor
{

  private static final String WEB_XML_PATH = "/WEB-INF/web.xml";

  private Vector              mMappings    = null;

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
  public WebConfigurationProcessor(PortletContext context)
  {

    if (context != null)
    {
      scanForFacesMappings(context);
    }

  } // END WebXmlProcessor

  public Vector getFacesMappings()
  {

    return mMappings;

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
      parser.parse(context.getResourceAsStream(WEB_XML_PATH), new WebXmlHandler());
    }
    catch (Exception e)
    {
      // TODO add logging
      // Do nothing
      ;
    }

  } // END scanForFacesMappings

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
  private class WebXmlHandler extends DefaultHandler
  {

    private static final String SERVLET_ELEMENT          = "servlet";
    private static final String SERVLET_NAME_ELEMENT     = "servlet-name";
    private static final String SERVLET_CLASS_ELEMENT    = "servlet-class";
    private static final String SERVLET_MAPPING_ELEMENT  = "servlet-mapping";
    private static final String URL_PATTERN_ELEMENT      = "url-pattern";
    private static final String FACES_SERVLET_DATA       = "javax.faces.webapp.FacesServlet";

    private boolean             mInServletElement        = false;
    private boolean             mInServletNameElement    = false;
    private boolean             mInServletClassElement   = false;

    private boolean             mInServletMappingElement = false;
    private boolean             mInURLPatternElement     = false;

    private String              mName                    = null;
    private String              mClass                   = null;

    private String              mFacesServletName        = null;
    private StringBuffer        mContent;

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException
    {

      return new InputSource(new StringReader(""));

    } // END resolveEntity

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
                                                                                               throws SAXException
    {

      boolean parseContent = false;

      if (SERVLET_ELEMENT.equals(localName))
      {
        mInServletElement = true;
      }
      else if (SERVLET_MAPPING_ELEMENT.equals(localName))
      {
        mInServletMappingElement = true;
      }
      else if (mInServletElement)
      {
        if (SERVLET_CLASS_ELEMENT.equals(localName))
        {
          mInServletClassElement = parseContent = true;
        }
        else if (SERVLET_NAME_ELEMENT.equals(localName))
        {
          mInServletNameElement = parseContent = true;
        }
      }
      else if (mInServletMappingElement)
      {
        if (URL_PATTERN_ELEMENT.equals(localName))
        {
          mInURLPatternElement = parseContent = true;
        }
        else if (SERVLET_NAME_ELEMENT.equals(localName))
        {
          mInServletNameElement = parseContent = true;
        }
      }
      if (parseContent)
      {
        mContent = new StringBuffer();
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

      if (mInServletClassElement)
      {
        mClass = mContent.toString().trim();
        mInServletClassElement = false;
      }
      else if (mInServletNameElement)
      {
        mName = mContent.toString().trim();
        mInServletNameElement = false;
      }
      else if (mInURLPatternElement)
      {
        if (mInServletMappingElement && mName != null && mFacesServletName != null
            && mName.equals(mFacesServletName))
        {
          if (mMappings == null)
          {
            mMappings = new Vector();
          }
          mMappings.add(mContent.toString().trim());
        }
        mInURLPatternElement = false;
      }
      else if (SERVLET_ELEMENT.equals(localName))
      {
        if (mName != null && mClass != null && mClass.equals(FACES_SERVLET_DATA))
        {
          mFacesServletName = mName;
        }
        mInServletElement = false;
      }
      else if (SERVLET_MAPPING_ELEMENT.equals(localName))
      {
        mInServletMappingElement = false;
      }

      mContent = null;

    } // END endElement

  } // END WebXmlHandler

}
