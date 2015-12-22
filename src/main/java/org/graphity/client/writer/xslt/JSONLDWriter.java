/**
 *  Copyright 2012 Martynas Jusevičius <martynas@graphity.org>
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
package org.graphity.client.writer.xslt;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.sparql.util.Context;
import com.sun.jersey.spi.resource.Singleton;
import java.io.OutputStream;
import java.io.Writer;
import javax.ws.rs.Produces;
import javax.ws.rs.ext.Provider;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.WriterGraphRIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.graphity.client.writer.ModelXSLTWriter;
import org.graphity.core.riot.RDFLanguages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Writes Model as JSON-LD.
 * 
 * @author Martynas Jusevičius <martynas@graphity.org>
 * @see <a href="http://json-ld.org">JSON-LD - JSON for Linking Data</a>
 */
@Provider
@Singleton
@Produces(org.graphity.core.MediaType.APPLICATION_LD_JSON)
public class JSONLDWriter extends ModelXSLTWriter implements WriterGraphRIOT
{
    private static final Logger log = LoggerFactory.getLogger(JSONLDWriter.class);
    
    public JSONLDWriter(Source stylesheet) throws TransformerConfigurationException
    {
        this(((SAXTransformerFactory)TransformerFactory.newInstance()).newTemplates(stylesheet));
    }
    
    public JSONLDWriter(Templates templates)
    {
	super(templates);
    }

    @Override
    public void write(OutputStream out, Graph graph, PrefixMap prefixMap, String baseURI, Context context)
    {
        try
        {
            write(ModelFactory.createModelForGraph(graph), out);
        }
        catch (TransformerException ex)
        {
            if (log.isDebugEnabled()) log.debug("Writing JSON-LD stream failed: {}", ex);            
            throw new RiotException(ex);
        }
    }

    @Override
    public void write(Writer out, Graph graph, PrefixMap prefixMap, String baseURI, Context context)
    {
        throw new UnsupportedOperationException("Use OutputStream instead of Writer");
    }

    @Override
    public Lang getLang()
    {
        return RDFLanguages.JSONLD;
    }

}