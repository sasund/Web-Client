/**
 *  Copyright 2012 Martynas Jusevičius <martynas@atomgraph.com>
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
package com.atomgraph.client.writer;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.vocabulary.RDF;
import com.sun.jersey.spi.resource.Singleton;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.Providers;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.rdf.model.RDFWriter;
import org.apache.jena.rdfxml.xmloutput.impl.Basic;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.checker.CheckerIRI;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import com.atomgraph.client.util.DataManager;
import com.atomgraph.client.util.OntologyProvider;
import com.atomgraph.client.util.XSLTBuilder;
import com.atomgraph.client.vocabulary.AC;
import com.atomgraph.client.vocabulary.LDT;
import com.atomgraph.core.util.Link;
import com.atomgraph.core.vocabulary.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms RDF with XSLT stylesheet and writes (X)HTML result to response.
 * Needs to be registered in the application.
 * 
 * @author Martynas Jusevičius <martynas@atomgraph.com>
 * @see <a href="http://jena.apache.org/documentation/javadoc/jena/com/hp/hpl/jena/rdf/model/Model.html">Model</a>
 * @see <a href="http://jsr311.java.net/nonav/javadoc/javax/ws/rs/ext/MessageBodyWriter.html">MessageBodyWriter</a>
 */
@Provider
@Singleton
@Produces({MediaType.APPLICATION_XHTML_XML,MediaType.TEXT_HTML}) // MediaType.APPLICATION_XML ?
public class ModelXSLTWriter implements MessageBodyWriter<Model> // WriterGraphRIOT?
{
    private static final Logger log = LoggerFactory.getLogger(ModelXSLTWriter.class);

    private final Templates templates;
    private final Map<Resource, MediaType> modeMediaTypeMap = new HashMap<>();
    
    {
        modeMediaTypeMap.put(AC.EditMode, MediaType.TEXT_HTML_TYPE);
        modeMediaTypeMap.put(AC.MapMode, MediaType.TEXT_HTML_TYPE);
    }
    
    @Context private UriInfo uriInfo;
    @Context private Request request;
    @Context private HttpHeaders httpHeaders;
    @Context private ServletConfig servletConfig;
    @Context private Providers providers;
    @Context private HttpServletRequest httpServletRequest;
    
    /**
     * Constructs from XSLT builder.
     * 
     * @param templates
     * @see com.atomgraph.client.util.XSLTBuilder
     */
    public ModelXSLTWriter(Templates templates)
    {
	if (templates == null) throw new IllegalArgumentException("Templates cannot be null");
	this.templates = templates;
    }

    public Providers getProviders()
    {
        return providers;
    }

    public DataManager getDataManager()
    {
	ContextResolver<DataManager> cr = getProviders().getContextResolver(DataManager.class, null);
	return cr.getContext(DataManager.class);
    }
    
    public ModelXSLTWriter()
    {
        this.templates = null;
    }
    
    @Override
    public void writeTo(Model model, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> headerMap, OutputStream entityStream) throws IOException
    {
	if (log.isTraceEnabled()) log.trace("Writing Model with HTTP headers: {} MediaType: {}", headerMap, mediaType);

	try
	{
            Templates stylesheet = getTemplates();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            //RDFWriter writer = model.getWriter(RDFLanguages.RDFXML.getName());
            RDFWriter writer = new Basic(); // workaround for Jena 3.0.1 bug: https://issues.apache.org/jira/browse/JENA-1168
            writer.setProperty("allowBadURIs", true); // round-tripping RDF/POST with user input may contain invalid URIs
            writer.write(model, baos, null);
            
            synchronized (this)
            {                
                getXSLTBuilder(XSLTBuilder.fromStylesheet(stylesheet).document(new ByteArrayInputStream(baos.toByteArray())),
                        headerMap).
                    resolver(getDataManager()).
                    result(new StreamResult(entityStream)).
                    transform();
            }
	}
	catch (TransformerException ex)
	{
	    if (log.isErrorEnabled()) log.error("XSLT transformation failed", ex);
	    throw new WebApplicationException(ex, Response.Status.INTERNAL_SERVER_ERROR); // TO-DO: make Mapper
	}
    }

    public void write(Model model, OutputStream entityStream) throws TransformerException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        model.write(baos, RDFLanguages.RDFXML.getName(), null);

        XSLTBuilder.fromStylesheet(getTemplates()).
            document(new ByteArrayInputStream(baos.toByteArray())).
            resolver(getDataManager()).
            result(new StreamResult(entityStream)).
            transform();        
    }
    
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return Model.class.isAssignableFrom(type);
    }

    @Override
    public long getSize(Model model, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
	return -1;
    }
    
    public Source getSource(Model model)
    {
	if (model == null) throw new IllegalArgumentException("Model cannot be null");	
	if (log.isDebugEnabled()) log.debug("Number of Model stmts read: {}", model.size());
	
	ByteArrayOutputStream stream = new ByteArrayOutputStream();
	model.write(stream, RDFLanguages.RDFXML.getName(), null);

	if (log.isDebugEnabled()) log.debug("RDF/XML bytes written: {}", stream.toByteArray().length);
	return new StreamSource(new ByteArrayInputStream(stream.toByteArray()));	
    }
    
    public Source getSource(OntModel ontModel, boolean writeAll)
    {
	if (!writeAll) return getSource(ontModel);
	if (ontModel == null) throw new IllegalArgumentException("OntModel cannot be null");	
	
	if (log.isDebugEnabled()) log.debug("Number of OntModel stmts read: {}", ontModel.size());
	
	ByteArrayOutputStream stream = new ByteArrayOutputStream();
	ontModel.writeAll(stream, Lang.RDFXML.getName(), null);

	if (log.isDebugEnabled()) log.debug("RDF/XML bytes written: {}", stream.toByteArray().length);
	return new StreamSource(new ByteArrayInputStream(stream.toByteArray()));	
    }
    
    public UriInfo getUriInfo()
    {
	return uriInfo;
    }

    public Request getRequest()
    {
        return request;
    }
    
    public HttpHeaders getHttpHeaders()
    {
	return httpHeaders;
    }

    public ServletConfig getServletConfig()
    {
        return servletConfig;
    }

    public HttpServletRequest getHttpServletRequest()
    {
        return httpServletRequest;
    }

    public Map<Resource, MediaType> getModeMediaTypeMap()
    {
        return modeMediaTypeMap;
    }
    
    public URI getMode()
    {
        if (getUriInfo().getQueryParameters().getFirst(AC.mode.getLocalName()) != null)
            return URI.create(getUriInfo().getQueryParameters().getFirst(AC.mode.getLocalName()));
        
        return null;
    }
    
    public URI getContextURI()
    {
        return URI.create(getHttpServletRequest().getRequestURL().toString()).
                resolve(getHttpServletRequest().getContextPath() + "/");
    }
    
    public Templates getTemplates()
    {
        if (templates != null) return templates;
        else
        {
            ContextResolver<Templates> cr = getProviders().getContextResolver(Templates.class, null);
            return cr.getContext(Templates.class);
        }
    }

    public XSLTBuilder getXSLTBuilder(XSLTBuilder builder, MultivaluedMap<String, Object> headerMap) throws TransformerConfigurationException
    {
	if (builder == null) throw new IllegalArgumentException("XSLTBuilder cannot be null");	
	if (headerMap == null) throw new IllegalArgumentException("MultivaluedMap cannot be null");	
        
        builder.parameter("{" + A.absolutePath.getNameSpace() + "}" + A.absolutePath.getLocalName(), getUriInfo().getAbsolutePath()).
        parameter("{" + A.requestUri.getNameSpace() + "}" + A.requestUri.getLocalName(), getUriInfo().getRequestUri()).
        parameter("{" + A.method.getNameSpace() + "}" + A.method.getLocalName(), getRequest().getMethod()).
        parameter("{" + A.httpHeaders.getNameSpace() + "}" + A.httpHeaders.getLocalName(), headerMap.toString()).
        parameter("{" + AC.contextUri.getNameSpace() + "}" + AC.contextUri.getLocalName(), getContextURI());
     
        try
        {
            URI typeHref = getLinkHref(headerMap, "Link", RDF.type.getLocalName());
            if (typeHref != null)
                builder.parameter("{" + RDF.type.getNameSpace() + "}" + RDF.type.getLocalName(), typeHref);

            URI baseHref = getLinkHref(headerMap, "Link", LDT.baseUri.getURI()); // LDT.baseUri?
            if (baseHref != null)
                builder.parameter("{" + LDT.baseUri.getNameSpace() + "}" + LDT.baseUri.getLocalName(), baseHref);
            
            URI ontologyHref = getLinkHref(headerMap, "Link", LDT.ontology.getURI());
            if (ontologyHref != null)                    
            {
                builder.parameter("{" + LDT.ontology.getNameSpace() + "}" + LDT.ontology.getLocalName(), ontologyHref);            

                OntModelSpec ontModelSpec = getOntModelSpec(getRules(headerMap, "Rules"));
                OntModel sitemap = getOntModel(ontologyHref.toString(), ontModelSpec);
                builder.parameter("{" + AC.sitemap.getNameSpace() + "}" + AC.sitemap.getLocalName(), getSource(sitemap, true));
            }
        }
        catch (URISyntaxException ex)
        {
            if (log.isErrorEnabled()) log.error("'Link' response header contains invalid URI: {}", headerMap.getFirst("Link"));
        }
	
        Object query = headerMap.getFirst("Query");
	if (query != null) builder.parameter("{http://spinrdf.org/spin#}query", query.toString());

        // workaround for Google Maps and Saxon-CE
        // They currently seem to work only in HTML mode and not in XHTML, because of document.write() usage
        // https://saxonica.plan.io/issues/1447
        // https://code.google.com/p/gmaps-api-issues/issues/detail?id=2820
        MediaType customMediaType = getCustomMediaType(getUriInfo());
        if (customMediaType != null)
	{
	    if (log.isDebugEnabled()) log.debug("Overriding response media type with '{}'", customMediaType);
            List<Object> contentTypes = new ArrayList();
            contentTypes.add(customMediaType);
            headerMap.put(HttpHeaders.CONTENT_TYPE, contentTypes);
	}
        
	Object contentType = headerMap.getFirst(HttpHeaders.CONTENT_TYPE);
	if (contentType != null)
	{
	    if (log.isDebugEnabled()) log.debug("Writing Model using XSLT media type: {}", contentType);
	    builder.outputProperty(OutputKeys.MEDIA_TYPE, contentType.toString());
	}
	Object contentLanguage = headerMap.getFirst(HttpHeaders.CONTENT_LANGUAGE);
	if (contentLanguage != null)
	{
	    if (log.isDebugEnabled()) log.debug("Writing Model using language: {}", contentLanguage.toString());
	    builder.parameter("{" + LDT.lang.getNameSpace() + "}" + LDT.lang.getLocalName(), contentLanguage.toString());
	}

        // pass HTTP query parameters into XSLT
	Iterator<Entry<String, List<String>>> paramIt = getUriInfo().getQueryParameters().entrySet().iterator();
        while (paramIt.hasNext())
        {
            Entry<String, List<String>> entry = paramIt.next();
            builder.parameter(entry.getKey(), entry.getValue().get(0)); // set string value
            if (log.isDebugEnabled()) log.debug("Setting XSLT param \"{}\" from HTTP query string with value: {}", entry.getKey(), entry.getValue().get(0));
        }

        // override the reserved parameters that need special types
	return setQueryParameters(builder, getUriInfo());
    }

    public XSLTBuilder setQueryParameters(XSLTBuilder builder, UriInfo uriInfo)
    {
	if (builder == null) throw new IllegalArgumentException("XSLTBuilder cannot be null");
	if (uriInfo == null) throw new IllegalArgumentException("UriInfo name cannot be null");

        /*
        // map uri query param values to g:requestUri XSLT param values
        if (uriInfo.getQueryParameters().getFirst(AC.uri.getLocalName()) != null)
	    bld.parameter("{" + A.requestUri.getNameSpace() + "}" + A.requestUri.getLocalName(),
                URI.create(uriInfo.getQueryParameters().getFirst(AC.uri.getLocalName())));
        */
	if (uriInfo.getQueryParameters().getFirst(LDT.lang.getLocalName()) != null)
	    builder.parameter("{" + LDT.lang.getNameSpace() + "}" + LDT.lang.getLocalName(),
                uriInfo.getQueryParameters().getFirst(LDT.lang.getLocalName()));
	if (uriInfo.getQueryParameters().getFirst(AC.endpointUri.getLocalName()) != null)
	    builder.parameter("{" + AC.endpointUri.getNameSpace() + "}" + AC.endpointUri.getLocalName(),
                URI.create(uriInfo.getQueryParameters().getFirst(AC.endpointUri.getLocalName())));
        
        return builder;
    }

    public OntModel getSitemap(MultivaluedMap<String, Object> headerMap, String ontologyURI)
    {
        return getOntModel(ontologyURI, getOntModelSpec(getRules(headerMap, "Rules")));        
    }
    
    public URI getLinkHref(MultivaluedMap<String, Object> headerMap, String headerName, String rel) throws URISyntaxException
    {
	if (headerMap == null) throw new IllegalArgumentException("Header Map cannot be null");
	if (headerName == null) throw new IllegalArgumentException("String header name cannot be null");
        if (rel == null) throw new IllegalArgumentException("Property Map cannot be null");
        
        List<Object> links = headerMap.get(headerName);
        if (links != null)
        {
            Iterator<Object> it = links.iterator();
            while (it.hasNext())
            {
                String linkHeader = it.next().toString();
                Link link = Link.valueOf(linkHeader);
                if (link.getRel().equals(rel)) return link.getHref();
            }
        }
        
        return null;
    }

    public OntModelSpec getOntModelSpec(List<Rule> rules)
    {
        OntModelSpec ontModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM);
        
        if (rules != null)
        {
            Reasoner reasoner = new GenericRuleReasoner(rules);
            //reasoner.setDerivationLogging(true);
            //reasoner.setParameter(ReasonerVocabulary.PROPtraceOn, Boolean.TRUE);
            ontModelSpec.setReasoner(reasoner);
        }
        
        return ontModelSpec;
    }
    
    public final List<Rule> getRules(MultivaluedMap<String, Object> headerMap, String headerName)
    {
        String rules = getRulesString(headerMap, headerName);
        if (rules == null) return null;
        
        return Rule.parseRules(rules);
    }
    
    public String getRulesString(MultivaluedMap<String, Object> headerMap, String headerName)
    {
	if (headerMap == null) throw new IllegalArgumentException("Header Map cannot be null");
	if (headerName == null) throw new IllegalArgumentException("String header name cannot be null");

        Object rules = headerMap.getFirst(headerName);
        if (rules != null) return rules.toString();
        
        return null;
    }
    
    public OntModel getOntModel(String ontologyURI, OntModelSpec ontModelSpec)
    {
        return new OntologyProvider().getOntModel(ontologyURI, ontModelSpec);
    }

    public MediaType getCustomMediaType(UriInfo uriInfo)
    {
	if (uriInfo == null) throw new IllegalArgumentException("UriInfo cannot be null");
        
        Resource mode = null;

        // TODO: improve dh:forClass handling
        if (uriInfo.getQueryParameters().getFirst("forClass") != null)
            mode = AC.EditMode; // this could be solved using a dummy ac:ConstructMode instead
        else
        {
            if (uriInfo.getQueryParameters().getFirst(AC.mode.getLocalName()) != null)
                mode = ResourceFactory.createResource(uriInfo.getQueryParameters().getFirst(AC.mode.getLocalName()));
        }
        
        if (mode != null && getModeMediaTypeMap().containsKey(mode))
             return getModeMediaTypeMap().get(mode);
         
        return null;
    }

    public IRI checkURI(String classIRIStr)
    {
	if (classIRIStr == null) throw new IllegalArgumentException("URI String cannot be null");

        IRI classIRI = IRIFactory.iriImplementation().create(classIRIStr);
        // throws Exceptions on bad URIs:
        CheckerIRI.iriViolations(classIRI, ErrorHandlerFactory.getDefaultErrorHandler());

        return classIRI;
    }

}