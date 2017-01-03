<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<field name="rptDesignFile"><hidden value="${reportPath}"/></field>
<field name="birtOutputFileName"><hidden value=""/></field>
<field name="fieldName"><hidden value="birtContentTypeField.append(serviceOrEntityName);"/></field>
<field name="birtContentType" title="${uiLabelMap.birtContentType}">
    <#--TODO replace by macro -->
    <drop-down>
        <option key="text/html" description="Text (.html)"/>
        <option key="application/pdf" description="Pdf (.pdf)"/>
        <option key="application/postscript" description="Postscript (.ps)"/>
        <option key="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" description="Excel (.xlsx)"/>
        <option key="application/vnd.openxmlformats-officedocument.wordprocessingml.document" description="Word (.docx)"/>
        <option key="application/vnd.openxmlformats-officedocument.presentationml.presentation" description="Powerpoint (.pptx)"/>
        <option key="application/vnd.ms-excel" description="Excel (.xls)"/>
        <option key="application/vnd.ms-word" description="Word (.doc)"/>
        <option key="application/vnd.ms-powerpoint" description="Powerpoint (.ppt)"/>
        <option key="application/vnd.oasis.opendocument.spreadsheet" description="LibreOffice Calc (.ods)"/>
        <option key="application/vnd.oasis.opendocument.text" description="LibreOffice Writer (.odt)"/>
        <option key="application/vnd.oasis.opendocument.presentation" description="LibreOffice Impress (.odp)"/>
    </drop-down>
</field>
<sort-order>
    <sort-field name="birtContentType"/>
</sort-order>