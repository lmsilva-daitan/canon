/*
 *
 *
 * Copyright 2017 Symphony Communication Services, LLC.
 *
 * Licensed to The Symphony Software Foundation (SSF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The SSF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.symphonyoss.s2.canon.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonyoss.s2.common.dom.json.IJsonDomNode;
import org.symphonyoss.s2.common.dom.json.IJsonObject;
import org.symphonyoss.s2.common.dom.json.ImmutableJsonObject;
import org.symphonyoss.s2.common.dom.json.JsonValue;
import org.symphonyoss.s2.common.dom.json.jackson.JacksonAdaptor;
import org.symphonyoss.s2.common.exception.InvalidValueException;
import org.symphonyoss.s2.common.fault.TransactionFault;
import org.symphonyoss.s2.fugue.di.Cardinality;
import org.symphonyoss.s2.fugue.di.ComponentDescriptor;
import org.symphonyoss.s2.fugue.di.IBinder;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ModelRegistry implements IModelRegistry
{
  private static final Logger LOG = LoggerFactory.getLogger(ModelRegistry.class);
  
  private Map<String, IEntityFactory<?,?,?>>  factoryMap_ = new HashMap<>();
//  private Map<String, IUrlPathServlet> servlets_ = new HashMap<>();
  private List<IModel>   models_ = new LinkedList<>();

  @Override
  public ComponentDescriptor getComponentDescriptor()
  {
    return new ComponentDescriptor()
        .addProvidedInterface(IModelRegistry.class)
        .addDependency(IModel.class, new IBinder<IModel>()
          {
            @Override
            public void bind(IModel v)
            {
              register(v);
            }
        }, Cardinality.oneOrMore);
  }
  
  @Override
  public IModelRegistry register(IModel factory)
  {
    models_.add(factory);
    factory.registerWith(this);
    return this;
  }
  
  @Override
  public IModelRegistry register(String name, IEntityFactory<?,?,?> factory)
  {
    factoryMap_.put(name, factory);
    return this;
  }
  
//  @Override
//  public void register(IUrlPathServlet servlet)
//  {
//    IUrlPathServlet other = servlets_.put(servlet.getUrlPath(), servlet);
//    
//    if(other != null)
//        throw new IllegalArgumentException("Duplicate path " + servlet.getUrlPath() + " with " + other);
//  }
//
//  @Override
//  public void registerServlets(IServletContainer servletContainer)
//  {
//    for(IUrlPathServlet servlet : servlets_.values())
//      servletContainer.addServlet(servlet);
//  }

  @Override
  public IEntity newInstance(ImmutableJsonObject jsonObject) throws InvalidValueException
  {
    String typeId = jsonObject.getString(CanonRuntime.JSON_TYPE);
    IEntityFactory<?,?,?> factory = factoryMap_.get(typeId);
    
    if(factory == null)
      throw new InvalidValueException("Unknown type \"" + typeId + "\"");
    
    return factory.newInstance(jsonObject);
  }
  
  public static ImmutableJsonObject parseOneJsonObject(Reader reader) throws InvalidValueException
  {
    ObjectMapper  mapper = new ObjectMapper().configure(Feature.AUTO_CLOSE_SOURCE, false);
    
    try
    {
      JsonNode tree = mapper.readTree(reader);
      
      IJsonDomNode adapted = JacksonAdaptor.adapt(tree);
      
      if(adapted instanceof IJsonObject)
      {
        return (ImmutableJsonObject) adapted.immutify();
      }
      else
      {
        throw new InvalidValueException("Expected a JSON Object but read a " + adapted.getClass().getName());
      }
    }
    catch(IOException e)
    {
      throw new InvalidValueException("Failed to parse input", e);
    }
  }
  
  public static JsonValue<?,?> parseOneJsonValue(Reader reader) throws InvalidValueException
  {
    ObjectMapper  mapper = new ObjectMapper().configure(Feature.AUTO_CLOSE_SOURCE, false);
    
    try
    {
      JsonNode tree = mapper.readTree(reader);
      
      if(tree.isValueNode())
      {
        return (JsonValue<?,?>)JacksonAdaptor.adapt(tree);
      }
      else
      {
        throw new InvalidValueException("Expected a JSON value but read a " + tree.getClass().getName());
      }
    }
    catch(IOException e)
    {
      throw new InvalidValueException("Failed to parse input", e);
    }
  }
  
  @Override
  public IEntity parseOne(Reader reader) throws IOException, InvalidValueException
  {
    return newInstance(parseOneJsonObject(reader));
  }

  @Override
  public void parseStream(InputStream in, IEntityConsumer consumer) throws InvalidValueException, IOException
  {
    JsonArrayParser arrayParser = new JsonArrayParser()
    {
      
      @Override
      protected void handle(String input)
      {
        try
        {
          IEntity result = parseOne(new StringReader(input));
          
          consumer.consume(result);
        }
        catch (InvalidValueException | IOException e)
        {
          // TODO I think handle needs to throw BadFormatException
          throw new TransactionFault(e);
        }
      }
    };
    
    byte[]      buf = new byte[1024];
    
    try
    {
      int nbytes;
      
      while((nbytes= in.read(buf)) != -1)
      {
        arrayParser.process(buf, nbytes);
      }
    }
    finally
    {
      arrayParser.close();
    }
  }

//  @Override
//  public Collection<IUrlPathServlet> getServlets()
//  {
//    return servlets_.values();
//  }

}
