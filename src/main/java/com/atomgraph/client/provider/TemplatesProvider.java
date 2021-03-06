/**
 *  Copyright 2013 Martynas Jusevičius <martynas@atomgraph.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.atomgraph.client.provider;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.PerRequestTypeInjectableProvider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.servlet.ServletConfig;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamSource;
import com.atomgraph.client.vocabulary.AC;
import com.atomgraph.core.exception.ConfigurationException;
import javax.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS provider for XSLT builder.
 * Needs to be registered in the application.
 * 
 * @author Martynas Jusevičius <martynas@atomgraph.com>
 * @see com.atomgraph.client.util.XSLTBuilder
 */
@Provider
public class TemplatesProvider extends PerRequestTypeInjectableProvider<Context, Templates> implements ContextResolver<Templates>
{

    private static final Logger log = LoggerFactory.getLogger(TemplatesProvider.class);

    private final ServletConfig servletConfig;
    private final URI stylesheetURI;
    private final boolean cacheStylesheet;
    private Templates templatesCache;

    /**
     * 
     * @param servletConfig
     */
    public TemplatesProvider(@Context ServletConfig servletConfig)
    {
	super(Templates.class);
        this.servletConfig = servletConfig;

        Object stylesheetURIParam = servletConfig.getInitParameter(AC.stylesheet.getURI());
        if (stylesheetURIParam == null)
        {
            if (log.isErrorEnabled()) log.error("XSLT stylesheet (ac:stylesheet) not configured");
            throw new ConfigurationException(AC.stylesheet);
        }
        try
        {
            stylesheetURI = new URI(stylesheetURIParam.toString());
        }
        catch (URISyntaxException ex)
        {
    	    if (log.isErrorEnabled()) log.error("XSLT stylesheet URI error", ex);
	    throw new WebApplicationException(ex, Response.Status.INTERNAL_SERVER_ERROR);
        }
        
        if (servletConfig.getInitParameter(AC.cacheStylesheet.getURI()) != null)
            cacheStylesheet = Boolean.parseBoolean(servletConfig.getInitParameter(AC.cacheStylesheet.getURI()));
        else cacheStylesheet = false;
    }

    public ServletConfig getServletConfig()
    {
        return servletConfig;
    }
    
    public URI getStylesheetURI()
    {
        return stylesheetURI;
    }
        
    public boolean cacheStylesheet()
    {
        return cacheStylesheet;
    }

    @Override
    public Injectable<Templates> getInjectable(ComponentContext cc, Context a)
    {
	return new Injectable<Templates>()
	{
	    @Override
	    public Templates getValue()
	    {
                return getTemplates();
	    }
	};
    }

    @Override
    public Templates getContext(Class<?> type)
    {
        return getTemplates();
    }

    protected Templates getTemplatesCache()
    {
        return templatesCache;
    }
    
    public Templates getTemplates()
    {
        try
        {            
            if (cacheStylesheet() && getTemplatesCache() != null) return getTemplatesCache();

            templatesCache = getTemplates(getSource(getServletConfig().getServletContext(),
                    getStylesheetURI().toString()));
            
            return getTemplatesCache();
        }
        catch (TransformerConfigurationException ex)
        {
	    if (log.isErrorEnabled()) log.error("XSLT transformer not configured property", ex);
	    throw new WebApplicationException(ex, Response.Status.INTERNAL_SERVER_ERROR);
        }
        catch (IOException ex)
        {
	    if (log.isErrorEnabled()) log.error("XSLT stylesheet not found or error reading it", ex);
	    throw new WebApplicationException(ex, Response.Status.INTERNAL_SERVER_ERROR);
        }
        catch (URISyntaxException ex)
        {
    	    if (log.isErrorEnabled()) log.error("XSLT stylesheet URI error", ex);
	    throw new WebApplicationException(ex, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    public Templates getTemplates(Source source) throws TransformerConfigurationException
    {
        return ((SAXTransformerFactory)TransformerFactory.newInstance()).newTemplates(source);
    }

    /**
     * Provides XML source from filename
     * 
     * @param servletContext
     * @param filename stylesheet filename
     * @return XML source
     * @throws FileNotFoundException
     * @throws URISyntaxException 
     * @throws java.net.MalformedURLException 
     * @see <a href="http://docs.oracle.com/javase/6/docs/api/javax/xml/transform/Source.html">Source</a>
     */
    public Source getSource(ServletContext servletContext, String filename) throws URISyntaxException, IOException
    {
	if (servletContext == null) throw new IllegalArgumentException("servletContext name cannot be null");	
        if (filename == null) throw new IllegalArgumentException("XML file name cannot be null");	

        if (log.isDebugEnabled()) log.debug("Resource paths used to load Source: {} from filename: {}", servletContext.getResourcePaths("/"), filename);
        URL xsltUrl = servletContext.getResource(filename);
	if (xsltUrl == null) throw new FileNotFoundException("File '" + filename + "' not found");
	String xsltUri = xsltUrl.toURI().toString();
	if (log.isDebugEnabled()) log.debug("XSLT stylesheet URI: {}", xsltUri);
	return new StreamSource(xsltUri);
    }
       
}