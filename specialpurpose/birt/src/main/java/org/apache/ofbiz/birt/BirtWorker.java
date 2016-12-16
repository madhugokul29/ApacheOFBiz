/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.birt;

import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.security.Security;
import org.apache.ofbiz.security.Security;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.LocalDispatcher;
import org.eclipse.birt.report.engine.api.EXCELRenderOption;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.HTMLServerImageHandler;
import org.eclipse.birt.report.engine.api.IPDFRenderOption;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.PDFRenderOption;
import org.eclipse.birt.report.engine.api.RenderOption;

public final class BirtWorker {

    public final static String module = BirtWorker.class.getName();

    private final static String BIRT_PARAMETERS = "birtParameters";
    private final static String BIRT_LOCALE = "birtLocale";
    private final static String BIRT_IMAGE_DIRECTORY = "birtImageDirectory";
    private final static String BIRT_CONTENT_TYPE = "birtContentType";
    private final static String BIRT_OUTPUT_FILE_NAME = "birtOutputFileName";

    private final static HTMLServerImageHandler imageHandler = new HTMLServerImageHandler();

    private BirtWorker() {}

    /**
     * export report
     * @param design
     * @param context
     * @param contentType
     * @param output
     * @throws EngineException
     * @throws GeneralException
     * @throws SQLException
     */
    public static void exportReport(IReportRunnable design, Map<String, ? extends Object> context, String contentType, OutputStream output)
        throws EngineException, GeneralException, SQLException {

        Locale birtLocale = (Locale)context.get(BIRT_LOCALE);
        String birtImageDirectory = (String)context.get(BIRT_IMAGE_DIRECTORY);

        if (contentType == null) {
            contentType = "text/html";
        }
        if (birtImageDirectory == null) {
             birtImageDirectory = "/";
        }
        Debug.logInfo("Get report engine", module);
        IReportEngine engine = BirtFactory.getReportEngine();

        IRunAndRenderTask task = engine.createRunAndRenderTask(design);
        if (birtLocale != null) {
            Debug.logInfo("Set BIRT locale:" + birtLocale, module);
            task.setLocale(birtLocale);
        }

        // set parameters if exists
        Map<String, Object> parameters = UtilGenerics.cast(context.get(BirtWorker.getBirtParameters()));
        if (parameters != null) {
            //Debug.logInfo("Set BIRT parameters:" + parameters, module);
            task.setParameterValues(parameters);
        }

        // set output options
        RenderOption options = new RenderOption();
        if ("text/html".equalsIgnoreCase(contentType)) { // HTML
            options.setOutputFormat(RenderOption.OUTPUT_FORMAT_HTML);
            HTMLRenderOption htmlOptions = new HTMLRenderOption(options);
            htmlOptions.setImageDirectory(birtImageDirectory);
            htmlOptions.setBaseImageURL(birtImageDirectory);
            options.setImageHandler(imageHandler);
        } else if ("application/postscript".equalsIgnoreCase(contentType)) { // Post Script
            options.setOutputFormat("postscript");
        } else if ("application/pdf".equalsIgnoreCase(contentType)) { // PDF
            options.setOutputFormat(RenderOption.OUTPUT_FORMAT_PDF);
            PDFRenderOption pdfOptions = new PDFRenderOption(options);
            pdfOptions.setOption(IPDFRenderOption.PAGE_OVERFLOW, Boolean.TRUE );
        } else if ("application/vnd.ms-word".equalsIgnoreCase(contentType)) { // MS Word
            options.setOutputFormat("doc");
        }  else if ("application/vnd.ms-excel".equalsIgnoreCase(contentType)) { // MS Excel
            options.setOutputFormat("xls");
            new EXCELRenderOption(options);
        } else if ("application/vnd.ms-powerpoint".equalsIgnoreCase(contentType)) { // MS Power Point
            options.setOutputFormat("ppt");
        } else if ("application/vnd.oasis.opendocument.text".equalsIgnoreCase(contentType)) { // Open Document Text
            options.setOutputFormat("odt");
        } else if ("application/vnd.oasis.opendocument.spreadsheet".equalsIgnoreCase(contentType)) { // Open Document Spreadsheet
            options.setOutputFormat("ods");
        } else if ("application/vnd.oasis.opendocument.presentation".equalsIgnoreCase(contentType)) { // Open Document Presentation
            options.setOutputFormat("odp");
        } else if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)) { // MS Word 2007
            options.setOutputFormat("docx");
        } else if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equalsIgnoreCase(contentType)) { // MS Excel 2007
            options.setOutputFormat("xlsx");
        } else if ("application/vnd.openxmlformats-officedocument.presentationml.presentation".equalsIgnoreCase(contentType)) { // MS Word 2007
            options.setOutputFormat("pptx");
        } else {
            throw new GeneralException("Unknown content type : " + contentType);
        }

        options.setOutputStream(output);
        task.setRenderOption(options);

        // run report
        Debug.logInfo("BIRT's locale is: " + task.getLocale(), module);
        Debug.logInfo("Run report's task", module);
        task.run();
        task.close();
    }
    
    /**
     * set web context objects
     * @param appContext
     * @param request
     * @param response
     */
    public static void setWebContextObjects(Map<String, Object> appContext, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        ServletContext servletContext = session.getServletContext();
        
        // set delegator
        Delegator delegator = (Delegator) session.getAttribute("delegator");
        if (UtilValidate.isEmpty(delegator)) {
            delegator = (Delegator) servletContext.getAttribute("delegator");
        }
        if (UtilValidate.isEmpty(delegator)) {
            delegator = (Delegator) request.getAttribute("delegator");
        }
        if (UtilValidate.isNotEmpty(delegator)) {
            appContext.put("delegator", delegator);
        }

        // set JDBC connection
        //appContext.put("OdaJDBCDriverPassInConnection", connection);

        // set dispatcher
        LocalDispatcher dispatcher = (LocalDispatcher) session.getAttribute("dispatcher");
        if (UtilValidate.isEmpty(dispatcher)) {
            dispatcher = (LocalDispatcher) servletContext.getAttribute("dispatcher");
        }
        if (UtilValidate.isEmpty(dispatcher)) {
            dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        }
        if (UtilValidate.isNotEmpty(dispatcher)) {
            appContext.put("dispatcher", dispatcher);
        }

        // set security
        Security security = (Security) session.getAttribute("security");
        if (UtilValidate.isEmpty(security)) {
            security = (Security) servletContext.getAttribute("security");
        }
        if (UtilValidate.isEmpty(security)) {
            security = (Security) request.getAttribute("security");
        }
        if (UtilValidate.isNotEmpty(security)) {
            appContext.put("security", security);
        }
    }

    public static String getBirtParameters() {
        return BIRT_PARAMETERS;
    }

    public static String getBirtLocale() {
        return BIRT_LOCALE;
    }

    public static String getBirtImageDirectory() {
        return BIRT_IMAGE_DIRECTORY;
    }

    public static String getBirtContentType() {
        return BIRT_CONTENT_TYPE;
    }

    public static String getBirtOutputFileName() {
        return BIRT_OUTPUT_FILE_NAME;
    }


    public static String getBirtStandardFields(String rptDesignName, String fieldName, String serviceOrEntityName) {
        String reportPath = UtilProperties.getPropertyValue("birt.properties", "rptDesign.output.path");
        StringBuffer birtContentTypeField = new StringBuffer("<field name=\"rptDesignFile\"><hidden value=\"");
        birtContentTypeField.append(reportPath);
        birtContentTypeField.append("/");
        birtContentTypeField.append(rptDesignName);
        birtContentTypeField.append("\"/></field>");
        birtContentTypeField.append("<field name=\"birtOutputFileName\"><hidden value=\"");
        birtContentTypeField.append(rptDesignName.substring(0, rptDesignName.lastIndexOf('.')));
        birtContentTypeField.append("\"/></field>");
        birtContentTypeField.append("<field name=\"");
        birtContentTypeField.append(fieldName);
        birtContentTypeField.append("\"><hidden value=\"");
        birtContentTypeField.append(serviceOrEntityName);
        birtContentTypeField.append("\"/></field>");
        birtContentTypeField.append("<field name=\"birtContentType\" title=\"${uiLabelMap.birtContentType}\">");
        birtContentTypeField.append("<drop-down>");
        birtContentTypeField.append("<option key=\"text/html\" description=\"Text (.html)\"/>");
        birtContentTypeField.append("<option key=\"application/pdf\" description=\"Pdf (.pdf)\"/>");
        birtContentTypeField.append("<option key=\"application/postscript\" description=\"Postscript (.ps)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\" description=\"Excel (.xlsx)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document\" description=\"Word (.docx)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.openxmlformats-officedocument.presentationml.presentation\" description=\"Powerpoint (.pptx)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.ms-excel\" description=\"Excel (.xls)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.ms-word\" description=\"Word (.doc)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.ms-powerpoint\" description=\"Powerpoint (.ppt)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.oasis.opendocument.spreadsheet\" description=\"LibreOffice Calc (.ods)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.oasis.opendocument.text\" description=\"LibreOffice Writer (.odt)\"/>");
        birtContentTypeField.append("<option key=\"application/vnd.oasis.opendocument.presentation\" description=\"LibreOffice Impress (.odp)\"/>");
        birtContentTypeField.append("</drop-down>");
        birtContentTypeField.append("</field>");
        birtContentTypeField.append("<sort-order>");
        birtContentTypeField.append("<sort-field name=\"birtContentType\"/>");
        birtContentTypeField.append("</sort-order>");
        return birtContentTypeField.toString();
    }

    public static String createReportContentInDatabase(Delegator delegator, LocalDispatcher dispatcher, String masterContentId, String rptDesignName, String description, String serviceName, String entityViewName, GenericValue userLogin) throws GenericServiceException{
        String dataResourceId = delegator.getNextSeqId("DataResource");
        String contentId = delegator.getNextSeqId("Content");
        String reportFormScreenName = masterContentId+"_"+contentId;

        if(UtilValidate.isEmpty(serviceName) && UtilValidate.isEmpty(entityViewName)){
            throw new GenericServiceException("Service and entity name cannot be both empty");
        }

        String fieldName = null;
        String serviceOrEntityName = null;
        String workflowType = null;
        if(UtilValidate.isEmpty(serviceName)){
            fieldName = "entityViewName";
            serviceOrEntityName = entityViewName;
            workflowType = "Entity";
        }else{
            fieldName = "serviceName";
            serviceOrEntityName = serviceName;
            workflowType = "Service";
        }
        StringBuffer reportForm = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        reportForm.append("<forms xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://ofbiz.apache.org/dtds/widget-form.xsd\">");
        reportForm.append("<form name=\"");
        reportForm.append(reportFormScreenName);
        reportForm.append("\" type=\"single\" target=\"produceReport\" extends=\"");
        reportForm.append(masterContentId);
        reportForm.append("\" extends-resource=\"component://birt/widget/birt/BirtMasterForms.xml\">");
        reportForm.append(getBirtStandardFields(rptDesignName, fieldName, serviceOrEntityName));
        reportForm.append("</form>");
        reportForm.append("</forms>");
        dispatcher.runSync("createDataResource", UtilMisc.toMap("dataResourceId", dataResourceId, "dataResourceTypeId", "ELECTRONIC_TEXT", "dataTemplateTypeId", "FORM_COMBINED", "userLogin", userLogin));
        dispatcher.runSync("createElectronicTextForm", UtilMisc.toMap("dataResourceId", dataResourceId, "textData", reportForm, "userLogin", userLogin));
        dispatcher.runSync("createContent", UtilMisc.toMap("contentId", contentId, "contentTypeId", "REPORT", "dataResourceId", dataResourceId, "statusId", "CTNT_IN_PROGRESS", "contentName", rptDesignName.substring(0, rptDesignName.indexOf('.')), "description", description, "userLogin", userLogin));
        String dataResourceIdRpt = delegator.getNextSeqId("DataResource");
        String contentIdRpt = delegator.getNextSeqId("Content");
        dispatcher.runSync("createDataResource", UtilMisc.toMap("dataResourceId", dataResourceIdRpt, "dataResourceTypeId", "LOCAL_FILE", "mimeTypeId", "text/rptdesign", "dataResourceName", rptDesignName, "objectInfo", UtilProperties.getPropertyValue("birt.properties", "rptDesign.output.path")+"/"+rptDesignName, "userLogin", userLogin));
        dispatcher.runSync("createContent", UtilMisc.toMap("contentId", contentIdRpt, "contentTypeId", "RPTDESIGN", "dataResourceId", dataResourceIdRpt, "statusId", "CTNT_PUBLISHED", "contentName", rptDesignName, "description", description+" (.rptDesign file)", "userLogin", userLogin));
        dispatcher.runSync("createContentAssoc", UtilMisc.toMap("contentId", masterContentId, "contentIdTo", contentId, "contentAssocTypeId", "SUB_CONTENT", "userLogin", userLogin));
        dispatcher.runSync("createContentAssoc", UtilMisc.toMap("contentId", contentId, "contentIdTo", contentIdRpt, "contentAssocTypeId", "SUB_CONTENT", "userLogin", userLogin));
        dispatcher.runSync("createContentAttribute", UtilMisc.toMap("contentId", contentId, "attrName", workflowType, "attrValue", serviceOrEntityName, "userLogin", userLogin));
        return contentId;
    }

    public static String getFormat(String contentType) throws GeneralException {
        if ("text/html".equalsIgnoreCase(contentType)) { // HTML
            return ".html";
        } else if ("application/postscript".equalsIgnoreCase(contentType)) { // Post Script
            return ".ps";
        } else if ("application/pdf".equalsIgnoreCase(contentType)) { // PDF
            return ".pdf";
        } else if ("application/vnd.ms-word".equalsIgnoreCase(contentType)) { // MS Word
            return ".doc";
        } else if ("application/vnd.ms-excel".equalsIgnoreCase(contentType)) { // MS Excel
            return ".xls";
        } else if ("application/vnd.ms-powerpoint".equalsIgnoreCase(contentType)) { // MS Power Point
            return ".ppt";
        } else if ("application/vnd.oasis.opendocument.text".equalsIgnoreCase(contentType)) { // Open Document Text
            return ".odt";
        } else if ("application/vnd.oasis.opendocument.spreadsheet".equalsIgnoreCase(contentType)) { // Open Document Spreadsheet
            return ".ods";
        } else if ("application/vnd.oasis.opendocument.presentation".equalsIgnoreCase(contentType)) { // Open Document Presentation
            return ".odp";
        } else if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)) { // MS Word 2007
            return ".docx";
        } else if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equalsIgnoreCase(contentType)) { // MS Excel 2007
            return ".xlsx";
        } else if ("application/vnd.openxmlformats-officedocument.presentationml.presentation".equalsIgnoreCase(contentType)) { // MS Word 2007
            return ".pptx";
        } else {
            throw new GeneralException("Unknown content type : " + contentType);
        }
    }

}