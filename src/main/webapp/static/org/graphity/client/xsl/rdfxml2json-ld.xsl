<?xml version="1.0" encoding="UTF-8"?>
<!--
Copyright 2012 Martynas Jusevičius <martynas@graphity.org>

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
<!DOCTYPE uridef[
	<!ENTITY owl	"http://www.w3.org/2002/07/owl#">
	<!ENTITY rdf	"http://www.w3.org/1999/02/22-rdf-syntax-ns#">
	<!ENTITY rdfs	"http://www.w3.org/2000/01/rdf-schema#">
	<!ENTITY xsd	"http://www.w3.org/2001/XMLSchema#">
	<!ENTITY gc	"http://graphity.org/gc#">
]>
<xsl:stylesheet version="2.0"
xmlns="http://www.w3.org/1999/xhtml"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
xmlns:owl="&owl;"
xmlns:rdf="&rdf;"
xmlns:rdfs="&rdfs;"
xmlns:xsd="&xsd;"
xmlns:gc="&gc;"
xmlns:xs="http://www.w3.org/2001/XMLSchema"
xmlns:date="http://exslt.org/dates-and-times"
exclude-result-prefixes="xs">

	<xsl:output indent="no" omit-xml-declaration="yes" method="text" encoding="UTF-8" media-type="application/ld+json"/>
	<xsl:strip-space elements="*"/>

	<xsl:key name="resources" match="*[*][@rdf:about] | *[*][@rdf:nodeID]" use="@rdf:about | @rdf:nodeID"/>
	<xsl:key name="predicates" match="*[@rdf:about or @rdf:nodeID]/*" use="concat(namespace-uri(), local-name())"/>
        <xsl:key name="predicates-by-object" match="*[@rdf:about]/* | *[@rdf:nodeID]/*" use="@rdf:resource | @rdf:nodeID"/>
    
	<xsl:template match="/">
	    <xsl:apply-templates mode="gc:JSONLDMode"/>
	</xsl:template>
	
	<xsl:template match="rdf:RDF" mode="gc:JSONLDMode">
[
         <!-- do not process blank nodes that are triple objects-->
	    <xsl:apply-templates select="*[@rdf:about or count(key('predicates-by-object', @rdf:nodeID)) &gt; 1]" mode="#current"/>
]
	</xsl:template>

	<!-- subject -->
	<xsl:template match="*[*][@rdf:about] | *[*][@rdf:nodeID]" mode="gc:JSONLDMode">
	{
	    <xsl:if test="@rdf:about">
		<xsl:apply-templates select="@rdf:about" mode="#current"/>,
	    </xsl:if>

	    <xsl:for-each-group select="*" group-by="concat(namespace-uri(), local-name())">
			<xsl:choose>
				<xsl:when test="current-grouping-key() = '&rdf;type'">"@type"</xsl:when>
				<xsl:otherwise>
					"<xsl:value-of select="current-grouping-key()"/>"
				</xsl:otherwise>
			</xsl:choose>
			:
			<xsl:choose>
			    <xsl:when test="count(current-group()) = 1">
					<xsl:apply-templates select="current-group()" mode="#current"/>
				</xsl:when>
				<xsl:otherwise>
					[
					<xsl:apply-templates select="current-group()" mode="#current"/>
					]
				</xsl:otherwise>
			</xsl:choose>		
			
			<xsl:if test="position() != last()">,
			</xsl:if>
	    </xsl:for-each-group>
	}
	
	    <xsl:if test="position() != last()">,
	    </xsl:if>
	</xsl:template>
	
	<!-- property -->
	<xsl:template match="*[@rdf:about or @rdf:nodeID]/rdf:type" mode="gc:JSONLDMode" priority="1">
	    "<xsl:value-of select="@rdf:resource"/>"
	    
	    <xsl:if test="position() != last()">,
	    </xsl:if>
	</xsl:template>

	<xsl:template match="*[@rdf:about or @rdf:nodeID]/*" mode="gc:JSONLDMode">
	    <xsl:apply-templates select="node() | @rdf:resource | @rdf:nodeID" mode="#current"/>

	    <xsl:if test="position() != last()">,
	    </xsl:if>
	</xsl:template>

	<xsl:template match="text()" mode="gc:JSONLDMode">
	    "<xsl:value-of select="."/>"
	</xsl:template>

	<xsl:template match="text()[../@rdf:datatype or ../@xml:lang]" mode="gc:JSONLDMode" priority="1">
		{
			"@value": "<xsl:value-of select="."/>"
			<xsl:if test="../@rdf:datatype">
			, <xsl:apply-templates select="../@rdf:datatype" mode="#current"/>
			</xsl:if>
			<xsl:if test="../@xml:lang">
			, <xsl:apply-templates select="../@xml:lang" mode="#current"/>
			</xsl:if>
		}
	</xsl:template>

	<xsl:template match="@rdf:about" mode="gc:JSONLDMode">
	    "@id": "<xsl:value-of select="."/>"
	</xsl:template>

	<xsl:template match="@rdf:resource" mode="gc:JSONLDMode">
		{
			"@id": "<xsl:value-of select="."/>"
		}
	</xsl:template>

	<xsl:template match="@rdf:nodeID" mode="gc:JSONLDMode">
	    "@id": "_:<xsl:value-of select="."/>"
	</xsl:template>

	<xsl:template match="*[@rdf:about or @rdf:nodeID]/*/@rdf:nodeID" mode="gc:JSONLDMode">
		{
			<xsl:next-match/>
		}
	</xsl:template>

	<xsl:template match="*[@rdf:about or @rdf:nodeID]/*/@rdf:nodeID[count(key('predicates-by-object', .)) &lt;= 1]" mode="gc:JSONLDMode">
		<xsl:param name="traversed-ids" as="xs:string*" tunnel="yes"/>
		<xsl:variable name="bnode" select="key('resources', .)"/>
			   
		<xsl:choose>
			<!-- loop if node not visited already -->
			<xsl:when test="not(. = $traversed-ids) and $bnode">
				<xsl:apply-templates select="$bnode" mode="#current">
					<xsl:with-param name="traversed-ids" select="(., $traversed-ids)" tunnel="yes"/>
				</xsl:apply-templates>
			</xsl:when>
			<xsl:otherwise>
				<xsl:next-match/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template match="@rdf:datatype" mode="gc:JSONLDMode">
	    "@type": "<xsl:value-of select="."/>"
	</xsl:template>

	<xsl:template match="@xml:lang" mode="gc:JSONLDMode">
	    "@language": "<xsl:value-of select="."/>"
	</xsl:template>
	
</xsl:stylesheet>