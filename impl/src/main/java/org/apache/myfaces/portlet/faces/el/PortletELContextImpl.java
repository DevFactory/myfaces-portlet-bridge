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

package org.apache.myfaces.portlet.faces.el;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

/**
 * Concrete implementation of {@link javax.el.ELContext}. ELContext's constructor is protected to
 * control creation of ELContext objects through their appropriate factory methods. This version of
 * ELContext forces construction through PortletFacesContextImpl.
 * 
 */
public class PortletELContextImpl extends ELContext
{

  private FunctionMapper mFunctionMapper;
  private VariableMapper mVariableMapper;
  private ELResolver     mResolver;

  /**
   * Constructs a new ELContext associated with the given ELResolver.
   */
  public PortletELContextImpl(ELResolver resolver)
  {
    mResolver = resolver;
  }

  public void setFunctionMapper(FunctionMapper fnMapper)
  {
    mFunctionMapper = fnMapper;
  }

  @Override
  public FunctionMapper getFunctionMapper()
  {
    return mFunctionMapper;
  }

  public void setVariableMapper(VariableMapper varMapper)
  {
    mVariableMapper = varMapper;
  }

  @Override
  public VariableMapper getVariableMapper()
  {
    return mVariableMapper;
  }

  @Override
  public ELResolver getELResolver()
  {
    return mResolver;
  }

}
