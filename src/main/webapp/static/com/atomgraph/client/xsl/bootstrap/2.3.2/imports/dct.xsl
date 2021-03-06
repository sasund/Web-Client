<?xml version="1.0" encoding="UTF-8"?>
<!--
Copyright 2012 Martynas Jusevičius <martynas@atomgraph.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<!DOCTYPE xsl:stylesheet [
    <!ENTITY rdf    "http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <!ENTITY dct    "http://purl.org/dc/terms/">
]>
<xsl:stylesheet version="2.0"
xmlns="http://www.w3.org/1999/xhtml"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
xmlns:xs="http://www.w3.org/2001/XMLSchema"
xmlns:rdf="&rdf;"
xmlns:dct="&dct;"
xmlns:bs2="http://graphity.org/xsl/bootstrap/2.3.2"
exclude-result-prefixes="#all">

    <!--
    <xsl:template match="dct:created | dct:modified | dct:issued" mode="bs2:InlinePropertyList">
        <dl class="pull-left" style="margin: 0; margin-right: 1em">
            <dt>
                <xsl:apply-templates select="."/>
            </dt>
            <dd>
                <xsl:apply-templates select="node() | @rdf:resource | @rdf:nodeID"/>
            </dd>
        </dl>
    </xsl:template>
    -->
    
    <xsl:template match="dct:title | dct:description | dct:subject" mode="bs2:PropertyList"/>
    
</xsl:stylesheet>