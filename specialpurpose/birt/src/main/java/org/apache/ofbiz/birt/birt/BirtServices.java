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
package org.apache.ofbiz.birt.birt;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.birt.BirtWorker;
import org.apache.ofbiz.birt.ReportDesignGenerator;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityConditionList;
import org.apache.ofbiz.entity.condition.EntityExpr;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.model.ModelReader;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.engine.api.script.IReportContext;
import org.eclipse.birt.report.model.api.DesignConfig;
import org.eclipse.birt.report.model.api.DesignElementHandle;
import org.eclipse.birt.report.model.api.DesignFileException;
import org.eclipse.birt.report.model.api.IDesignEngine;
import org.eclipse.birt.report.model.api.IDesignEngineFactory;
import org.eclipse.birt.report.model.api.ReportDesignHandle;
import org.eclipse.birt.report.model.api.SessionHandle;
import org.eclipse.birt.report.model.api.SimpleMasterPageHandle;
import org.eclipse.birt.report.model.api.SlotHandle;
import org.eclipse.birt.report.model.api.VariableElementHandle;
import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;
import org.eclipse.birt.report.model.elements.SimpleMasterPage;

import com.ibm.icu.util.ULocale;


/**
 * Birt Services
 */

public class BirtServices {
    
    public static final String module = BirtServices.class.getName();
    public static final String resource = "BirtUiLabels";
    public static final String resource_error = "BirtErrorUiLabels";
    public static final String resourceProduct = "BirtUiLabels";

    public static Map<String, Object> generateReport(DispatchContext dctx, Map<String, Object> context) {
        ReportDesignGenerator rptGenerator;
        try {
            rptGenerator = new ReportDesignGenerator(context, dctx);
        } catch (Exception e1) {
            e1.printStackTrace();
            return ServiceUtil.returnError(e1.getMessage());
        }
        try {
            rptGenerator.buildReport();
        } catch (Exception e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }
        return ServiceUtil.returnSuccess();
    }
    
    @Deprecated
    public static Map<String, Object> getListMultiFieldsByView(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        String entityViewName = (String) context.get("entityViewName");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        List<String> listMultiFields = new ArrayList<String>();
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> result = new HashMap<String, Object>();
        Locale locale = (Locale) context.get("locale");

        ModelEntity modelEntity = delegator.getModelEntity(entityViewName);
        List<String> listFieldsEntity = modelEntity.getAllFieldNames();

        for(String field: listFieldsEntity){
            listMultiFields.add(field);
            ModelField mField = modelEntity.getField(field);
            String fieldType = mField.getType();
            String birtType = null;
            try {
                Map<String, Object> convertRes = dispatcher.runSync("convertFieldTypeToBirtType", UtilMisc.toMap("fieldType", fieldType, "userLogin", userLogin));
                birtType = (String) convertRes.get("birtType");
                if(UtilValidate.isEmpty(birtType)){
                    return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "conversion.field_to_birt.failed", locale));
                }
            } catch (GenericServiceException e) {
                e.printStackTrace();
            }
         // make more general when report forms have been made so too.
            if(birtType.equalsIgnoreCase("date-time") || birtType.equalsIgnoreCase("date") || birtType.equalsIgnoreCase("time")){
                listMultiFields.add(field+"_fld0_op");
                listMultiFields.add(field+"_fld0_value");
                listMultiFields.add(field+"_fld1_op");
                listMultiFields.add(field+"_fld1_value");
            }
        }
        result.put("listMultiFields", listMultiFields);
        return result;
    }

    public static Map<String, Object> callPerformFindFromBirt(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        IReportContext reportContext = (IReportContext) context.get("reportContext");
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String entityViewName = (String) reportContext.getParameterValue("entityViewOrServiceName");
        Map<String, Object> inputFields = (Map<String, Object>) reportContext.getParameterValue("parameters");
        Map<String, Object> resultPerformFind = new HashMap<String, Object>();
        Map<String, Object> resultToBirt = null;
        List<GenericValue> list = null;

        if(UtilValidate.isEmpty(entityViewName)) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "unknown.entityViewName", locale));
        }

        try {
            resultPerformFind = dispatcher.runSync("performFind", UtilMisc.<String, Object> toMap("entityName", entityViewName, "inputFields", inputFields, "userLogin", userLogin, "noConditionFind", "Y", "locale", locale));
            if(ServiceUtil.isError(resultPerformFind)) {
                return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "errorRunningPerformFind", locale));
            }
        } catch (GenericServiceException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }

        EntityListIterator listIt = (EntityListIterator) resultPerformFind.get("listIt");
        try {
            if(UtilValidate.isNotEmpty(listIt)) {
                list = listIt.getCompleteList();
                listIt.close();
            } else {
                return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "errorRunningPerformFind", locale));
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        resultToBirt = ServiceUtil.returnSuccess();
        resultToBirt.put("list", list);
        return resultToBirt;
    }
    
    public static Map<String, Object> determineReportGenerationPath(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        
        String reportName = (String) context.get("reportName");
        String masterContentId = (String) context.get("contentId");
        String description = (String) context.get("description");
        String writeFilters = (String) context.get("writeFilters");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        // check if .rptdesign file already exists under this name and increment index name if needed
        String rptDesignName = reportName.concat("_generated.rptdesign");
        List<GenericValue> listRptDesigns = null;
        EntityCondition entityConditionRpt = EntityCondition.makeCondition("contentTypeId", EntityOperator.EQUALS, "RPTDESIGN");
        EntityCondition entityConditionOnName = EntityCondition.makeCondition("drObjectInfo", EntityOperator.EQUALS, UtilProperties.getPropertyValue("birt.properties", "rptDesign.output.path").concat("/").concat(rptDesignName));
        List<EntityCondition> listConditions = new ArrayList<EntityCondition>();
        listConditions.add(entityConditionRpt);
        listConditions.add(entityConditionOnName);
        EntityConditionList<EntityCondition> ecl = EntityCondition.makeCondition(listConditions, EntityOperator.AND);
        try {
            listRptDesigns = delegator.findList("ContentDataResourceView", ecl, UtilMisc.toSet("drObjectInfo"), null, null, false);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        int i = 1;
        while(UtilValidate.isNotEmpty(listRptDesigns)){
            StringBuffer rptDesignNameSb = new StringBuffer(reportName);
            rptDesignNameSb.append("_generated(");
            rptDesignNameSb.append(i);
            rptDesignNameSb.append(").rptdesign");
            rptDesignName = rptDesignNameSb.toString();
            listConditions.remove(entityConditionOnName);
            entityConditionOnName = EntityCondition.makeCondition("drObjectInfo", EntityOperator.EQUALS, UtilProperties.getPropertyValue("birt.properties", "rptDesign.output.path").concat("/").concat(rptDesignName));
            listConditions.add(entityConditionOnName);
            ecl = EntityCondition.makeCondition(listConditions, EntityOperator.AND);
            try {
                listRptDesigns = delegator.findList("ContentDataResourceView", ecl, UtilMisc.toSet("drObjectInfo"), null, null, false);
            } catch (GenericEntityException e) {
                e.printStackTrace();
                return ServiceUtil.returnError(e.getMessage());
            }
            i++;
        }
        
        GenericValue contentAttribute = null;
        List<GenericValue> listContentAttribute;
        try {
            EntityCondition entityCondition = EntityCondition.makeCondition("contentId", EntityOperator.EQUALS, masterContentId);
            listContentAttribute = delegator.findList("ContentAttribute", entityCondition, null, null, null, false);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        
        if(UtilValidate.isEmpty(listContentAttribute)) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "no_attribute_found", locale));
        }
        contentAttribute = listContentAttribute.get(0);
        String attrName = contentAttribute.getString("attrName");
        String reportContentId;
        if(attrName.equalsIgnoreCase("Entity")){
            String entityViewName = contentAttribute.getString("attrValue");
            try {
                Map<String, Object> result = dispatcher.runSync("checkEntityViewExistence", UtilMisc.toMap("entityName", entityViewName));
                if(ServiceUtil.isError(result)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
            } catch (GenericServiceException e) {
                e.printStackTrace();
                return ServiceUtil.returnError(e.getMessage());
            }
            try {
                Map<String, Object> resultContent = dispatcher.runSync("createReportContentFromMasterEntityWorkflow", UtilMisc.toMap("entityViewName", entityViewName, "rptDesignName", rptDesignName, "description", description, "writeFilters", writeFilters, "masterContentId", masterContentId, "userLogin", userLogin, "locale", locale));
                if(ServiceUtil.isError(resultContent)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(resultContent));
                }
                reportContentId = (String) resultContent.get("contentId");
            } catch (GenericServiceException e) {
                e.printStackTrace();
                return ServiceUtil.returnError(e.getMessage());
            }
        }else if(attrName.equalsIgnoreCase("Service")){
            String serviceName = contentAttribute.getString("attrValue");
            try {
                Map<String, Object> resultContent = dispatcher.runSync("createReportContentFromMasterServiceWorkflow", UtilMisc.toMap("serviceName", serviceName, "rptDesignName", rptDesignName, "description", description, "writeFilters", writeFilters, "masterContentId", masterContentId, "userLogin", userLogin, "locale", locale));
                if(ServiceUtil.isError(resultContent)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(resultContent));
                }
                reportContentId = (String) resultContent.get("contentId");
            } catch (GenericServiceException e) {
                e.printStackTrace();
                return ServiceUtil.returnError(e.getMessage());
            }
        }else{
            // could create other workflows. WebService? Does it need to be independent from Service workflow?
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "cannot_determine_data_source", locale));
        }

        // prepare report form to display to allow override
        String textForm;
        Map<String, Object> resultFormDisplay;
        try {
            resultFormDisplay = dispatcher.runSync("createFormForDisplay", UtilMisc.toMap("reportContentId", reportContentId, "userLogin", userLogin, "locale", locale));
            textForm = (String) resultFormDisplay.get("textForm");
        } catch (GenericServiceException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "error_creating_default_form", locale).concat(UtilProperties.getMessage(resource, "withMessage", locale)).concat(e.getMessage()));
        }

        Map<String, Object> result = ServiceUtil.returnSuccess(UtilProperties.getMessage(resource, "report_successfully_generated", locale).concat(" ").concat(rptDesignName));
        result.put("textForm", textForm);
        result.put("reportContentId", reportContentId);
        return result;
    }

    public static Map<String, Object> checkEntityViewExistence(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        ModelReader reader = delegator.getModelReader();
        String entityName = (String) context.get("entityName");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        Set<String> entityNames = null;
        try {
            entityNames = reader.getEntityNames();
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "failed_to_retrieve_entity_names", locale));
        }
        if(!entityNames.contains(entityName)){
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "entity_view_does_not_exist", locale));
        }
        return result;
    }

    // I'm not a big fan of how I did the createFormForDisplay / overrideReportForm. Could probably be improved using a proper formForReport object or something similar.
    public static Map<String, Object> overrideReportForm(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String reportContentId = (String) context.get("reportContentId");
        String overrideFilters = (String) context.get("overrideFilters");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        // safety check : do not accept "${groovy", "${bsh" and "javascript"
        String overideFiltersNoWhiteSpace = overrideFilters.replaceAll("\\s", "");
        if(overideFiltersNoWhiteSpace.contains("${groovy") || overideFiltersNoWhiteSpace.contains("${bsh") || overideFiltersNoWhiteSpace.contains("javascript:")) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "UnauthorisedCharacter", locale));
        }

        GenericValue content;
        List<GenericValue> contentAssocViewTos;
        GenericValue contentAssocViewTo;
        List<GenericValue> contentAttributes;
        GenericValue contentAttribute;
        String dataResourceId;
        String rptDesignName;
        String fieldName;
        String serviceOrEntityName;
        try {
            content = delegator.findOne("Content", true, UtilMisc.toMap("contentId", reportContentId));
            dataResourceId = content.getString("dataResourceId");
            EntityExpr conditionAssocType = EntityCondition.makeCondition("caContentAssocTypeId", "SUB_CONTENT");
            EntityExpr conditionContentIdStart = EntityCondition.makeCondition("contentIdStart", reportContentId);
            EntityExpr conditionContentTypeId = EntityCondition.makeCondition("contentTypeId", "RPTDESIGN");
            EntityConditionList<EntityExpr> ecl = EntityCondition.makeCondition(UtilMisc.toList(conditionAssocType, conditionContentIdStart, conditionContentTypeId));
            contentAssocViewTos = delegator.findList("ContentAssocViewTo", ecl, UtilMisc.toSet("contentName"), null, null, true);
            contentAssocViewTo = contentAssocViewTos.get(0);
            rptDesignName = contentAssocViewTo.getString("contentName");

            EntityExpr conditionAttrNameEntity = EntityCondition.makeCondition("attrName", "Entity");
            EntityExpr conditionAttrNameService = EntityCondition.makeCondition("attrName", "Service");
            EntityConditionList<EntityExpr> conditionAttrName = EntityCondition.makeCondition(UtilMisc.toList(conditionAttrNameEntity, conditionAttrNameService), EntityOperator.OR);
            EntityExpr conditionContentId = EntityCondition.makeCondition("contentId", reportContentId);
            EntityConditionList<EntityCondition> eclAttribute = EntityCondition.makeCondition(conditionAttrName, conditionContentId);
            contentAttributes = delegator.findList("ContentAttribute", eclAttribute, UtilMisc.toSet("attrName", "attrValue"), null, null, true);
            contentAttribute = contentAttributes.get(0);
            serviceOrEntityName = contentAttribute.getString("attrValue");
            if(contentAttribute.getString("attrName").equalsIgnoreCase("entity")) {
                fieldName = "entityViewName";
            } else {
                fieldName = "serviceName";
            }
        } catch (GenericEntityException e1) {
            e1.printStackTrace();
            return ServiceUtil.returnError(e1.getMessage());
        }
        try {
            StringBuffer overrideForm = new StringBuffer(overrideFilters);
            int indexEndForm = overrideForm.indexOf("</form>");
            String formStart = overrideForm.substring(0, indexEndForm);
            String formEnd = overrideForm.substring(indexEndForm, overrideForm.length());
            StringBuffer newForm = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <forms xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://ofbiz.apache.org/dtds/widget-form.xsd\">");
            newForm.append(formStart);
            newForm.append(BirtWorker.getBirtStandardFields(rptDesignName, fieldName, serviceOrEntityName));
            newForm.append(formEnd);
            newForm.append("</forms>");
            dispatcher.runSync("updateElectronicTextForm", UtilMisc.toMap("dataResourceId", dataResourceId, "textData", newForm.toString(), "userLogin", userLogin, "locale", locale));
        } catch (Exception e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        return ServiceUtil.returnSuccess(UtilProperties.getMessage(resource, "form_successfully_overridden", locale));
    }

    public static Map<String, Object> createReportContentFromMasterEntityWorkflow(DispatchContext dctx, Map<String, Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String description = (String) context.get("description");
        String rptDesignName = (String) context.get("rptDesignName");
        String writeFilters = (String) context.get("writeFilters");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String entityViewName = (String) context.get("entityViewName");
        String masterContentId = (String) context.get("masterContentId");

        ModelEntity modelEntity = delegator.getModelEntity(entityViewName);
        String contentId = null;
        Map<String, Object> result = ServiceUtil.returnSuccess();
        try {
            Map<String, Object> resultMapsForGeneration = dispatcher.runSync("createBirtMaps", UtilMisc.toMap("modelEntity", modelEntity, "userLogin", userLogin, "locale", locale));
            if(ServiceUtil.isError(resultMapsForGeneration)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(resultMapsForGeneration));
            }
            Map<String, String> dataMap = (Map<String, String>) resultMapsForGeneration.get("dataMap");
            Map<String, String> fieldDisplayLabels = null;
            if(UtilValidate.isNotEmpty(resultMapsForGeneration.get("fieldDisplayLabels"))) {
                fieldDisplayLabels = (Map<String, String>) resultMapsForGeneration.get("fieldDisplayLabels");
            }
            Map<String, String> filterMap = null;
            if(UtilValidate.isNotEmpty(resultMapsForGeneration.get("filterMap"))) {
                filterMap = (Map<String, String>) resultMapsForGeneration.get("filterMap");
            }
            Map<String, String> filterDisplayLabels = null;
            if(UtilValidate.isNotEmpty(resultMapsForGeneration.get("filterDisplayLabels"))) {
                filterDisplayLabels = (Map<String, String>) resultMapsForGeneration.get("filterDisplayLabels");
            }
            contentId = BirtWorker.recordReportContent(delegator, dispatcher, context);
            // callPerformFindFromBirt is the customMethod for Entity workflow
            result = dispatcher.runSync("reportGeneration", UtilMisc.toMap("dataMap", dataMap, "fieldDisplayLabels", fieldDisplayLabels, "filterMap", filterMap, "filterDisplayLabels", filterDisplayLabels, "rptDesignName", rptDesignName, "writeFilters", writeFilters, "customMethod", "callPerformFindFromBirt", "userLogin", userLogin, "locale", locale));
            if(ServiceUtil.isError(result)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
            }
        } catch (GeneralException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        result.put("contentId", contentId);
        return result;
    }

    public static Map<String, Object> createBirtMaps(DispatchContext dctx, Map<String, ? extends Object> context) {
        Locale locale = (Locale) context.get("locale");
        ModelEntity modelEntity = (ModelEntity) context.get("modelEntity");

        Map<String, String> dataMap = new HashMap<String, String>();
        Map<String, String> fieldDisplayLabels = new HashMap<String, String>();
        LinkedHashMap<String, String> filterMap = new LinkedHashMap<String, String>();
        LinkedHashMap<String, String> filterDisplayLabels = new LinkedHashMap<String, String>();

        List<String> listEntityFields = modelEntity.getAllFieldNames();
        Map<Object, Object> uiLabelMap = new HashMap<Object, Object>();
        final String[] resourceGlob = {"OrderUiLabels", "ProductUiLabels", "PartyUiLabels", "ContentUiLabels", "AccountingUiLabels", "CommonUiLabels", "BirtUiLabels"};
        for (String res : resourceGlob) {
            uiLabelMap.putAll(UtilProperties.getProperties(res, locale));
        }

        for (String field: listEntityFields) {
            ModelField mField = modelEntity.getField(field);
            dataMap.put(field, mField.getType());
            
            String localizedName = null;
            String interpretedFieldName = null;
            FlexibleStringExpander.getInstance(mField.getDescription()).expandString(context);
            if (uiLabelMap == null) {
                Debug.log("Could not find UiLabelMap in context while generating report");
            } else {
                String titleFieldName = "FormFieldTitle_".concat(field);
                localizedName = (String) uiLabelMap.get(titleFieldName);
                if (UtilValidate.isEmpty(localizedName) || localizedName.equals(titleFieldName)) {
                    interpretedFieldName = FlexibleStringExpander.getInstance(field).expandString(context);
                    fieldDisplayLabels.put(field, interpretedFieldName);
                } else {
                    fieldDisplayLabels.put(field, localizedName);
                }
            }
    
            List<String> listTwoFields = UtilMisc.toList("id", "id-long", "id-vlong", "indicator", "very-short", "short-varchar", "long-varchar", "very-long", "comment");
            listTwoFields.add("description");
            listTwoFields.add("name");
            listTwoFields.add("value");
            listTwoFields.add("credit-card-number");
            listTwoFields.add("credit-card-date");
            listTwoFields.add("email");
            listTwoFields.add("url");
            listTwoFields.add("id-ne");
            listTwoFields.add("id-long-ne");
            listTwoFields.add("id-vlong-ne");
            listTwoFields.add("tel-number");
            listTwoFields.add("fixed-point"); // should be in the other category, OFBiz bug (https://issues.apache.org/jira/browse/OFBIZ-6443) delete line when corrected.
            listTwoFields.add("currency-precise"); // should be in the other category, OFBiz bug (https://issues.apache.org/jira/browse/OFBIZ-6443) delete line when corrected.
            if(listTwoFields.contains(mField.getType())) {
                filterMap.put(field, mField.getType());
                filterMap.put(field.concat("_op"), "short-varchar");
                filterDisplayLabels.put(field, fieldDisplayLabels.get(field));
                filterDisplayLabels.put(field.concat("_op"), fieldDisplayLabels.get(field).concat(UtilProperties.getMessage(resource, "operator", locale)));
            } else { // remaining types need 4 fields (fld0-1_op-value)
                filterMap.put(field.concat("_fld0_value"), mField.getType());
                filterMap.put(field.concat("_fld0_op"), "short-varchar");
                filterMap.put(field.concat("_fld1_value"), mField.getType());
                filterMap.put(field.concat("_fld1_op"), "short-varchar");
                filterDisplayLabels.put(field.concat("_fld0_value"), fieldDisplayLabels.get(field).concat(UtilProperties.getMessage(resource, "fieldZero", locale)));
                filterDisplayLabels.put(field.concat("_fld0_op"), fieldDisplayLabels.get(field).concat(UtilProperties.getMessage(resource, "fieldZero", locale).concat(UtilProperties.getMessage(resource, "operator", locale))));
                filterDisplayLabels.put(field.concat("_fld1_value"), fieldDisplayLabels.get(field).concat(UtilProperties.getMessage(resource, "fieldOne", locale)));
                filterDisplayLabels.put(field.concat("_fld1_op"), fieldDisplayLabels.get(field).concat(UtilProperties.getMessage(resource, "fieldOne", locale).concat(UtilProperties.getMessage(resource, "operator", locale))));
            }
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("dataMap", dataMap);
        if(UtilValidate.isNotEmpty(fieldDisplayLabels)) {
            result.put("fieldDisplayLabels", fieldDisplayLabels);
        }
        if(UtilValidate.isNotEmpty(filterMap)) {
        result.put("filterMap", filterMap);
        }
        if(UtilValidate.isNotEmpty(filterDisplayLabels)) {
        result.put("filterDisplayLabels", filterDisplayLabels);
        }
        return result;
    }

    public static Map<String, Object> createFormForDisplay(DispatchContext dctx, Map<String, ? extends Object> context) {
        String reportContentId = (String) context.get("reportContentId");
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();

        String textData;
        try {
            GenericValue content = delegator.findOne("Content", true, UtilMisc.toMap("contentId", reportContentId));
            String dataResourceId = content.getString("dataResourceId");
            GenericValue electronicText = delegator.findOne("ElectronicText", true, UtilMisc.toMap("dataResourceId", dataResourceId));
            textData = electronicText.getString("textData");
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        textData = textData.substring(textData.indexOf("<form name=\"CTNT_MASTER_"), textData.length());
        textData = textData.substring(0, textData.indexOf("</forms>"));
        textData = StringUtil.replaceString(textData, textData.substring(textData.indexOf("<field name=\"rptDesignFile\">"), textData.indexOf("</drop-down></field>")+20), "");
        textData = StringUtil.replaceString(textData, textData.substring(textData.indexOf("<sort-order>"), textData.indexOf("</form>")), "\n\n");
//        String textFormString = UtilFormatOut.encodeXmlValue(textData);
        String textFormString = textData;
        textFormString = StringUtil.replaceString(textFormString, "$", "&#36;");
        result.put("textForm", textFormString);
        return result;
    }

    public static Map<String, Object> createReportContentFromMasterServiceWorkflow(DispatchContext dctx, Map<String, Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String description = (String) context.get("description");
        String rptDesignName = (String) context.get("rptDesignName");
        String writeFilters = (String) context.get("writeFilters");
        String serviceName = (String) context.get("serviceName");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String masterContentId = (String) context.get("masterContentId");
        String contentId = null;
        Map<String, Object> result = ServiceUtil.returnSuccess();

        try {
            Map<String, Object> resultService = dispatcher.runSync(serviceName, UtilMisc.toMap("locale", locale, "userLogin", userLogin));
            Map<String, String> dataMap = (Map<String, String>) resultService.get("dataMap");
            Map<String, String> filterMap = (Map<String, String>) resultService.get("filterMap");
            Map<String, String> fieldDisplayLabels = (Map<String, String>) resultService.get("fieldDisplayLabels");
            Map<String, String> filterDisplayLabels = (Map<String, String>) resultService.get("filterDisplayLabels");
            String customMethodName = (String) resultService.get("customMethodName");
            contentId = BirtWorker.recordReportContent(delegator, dispatcher, context);
            Map<String, Object> resultGeneration = dispatcher.runSync("reportGeneration", UtilMisc.toMap("dataMap", dataMap, "fieldDisplayLabels", fieldDisplayLabels, "filterMap", filterMap, "filterDisplayLabels", filterDisplayLabels, "rptDesignName", rptDesignName, "writeFilters", writeFilters, "customMethod", customMethodName, "userLogin", userLogin, "locale", locale));
            if(ServiceUtil.isError(resultGeneration)) {
                return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "errorCreatingReport", locale));
            }
        } catch (GeneralException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        result.put("contentId", contentId);
        return result;
    }

    public static Map<String, Object> deleteAllReports(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        List<String> listContentId = null;
        List<String> listRptDesignFiles = null;
        List<GenericValue> listRptDesignFilesGV = null;
        List<GenericValue> listContent = null;
        EntityCondition entityConditionContent = EntityCondition.makeCondition("contentTypeId", EntityOperator.EQUALS, "REPORT");
        EntityCondition entityConditionContentRpt = EntityCondition.makeCondition("contentTypeId", EntityOperator.EQUALS, "RPTDESIGN");
        try {
            listContent = delegator.findList("Content", entityConditionContent, UtilMisc.toSet("contentId"), null, null, false);
            listRptDesignFilesGV = delegator.findList("ContentDataResourceView", entityConditionContentRpt, UtilMisc.toSet("drObjectInfo"), null, null, false);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        if(UtilValidate.isEmpty(listContent)) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "noReportToDelete", locale));
        }
        listContentId = EntityUtil.getFieldListFromEntityList(listContent, "contentId", false);
        listRptDesignFiles = EntityUtil.getFieldListFromEntityList(listRptDesignFilesGV, "drObjectInfo", false);
        for(String rptfileName: listRptDesignFiles) {
            Path path = Paths.get(rptfileName.toString());
            try {
                if(!Files.deleteIfExists(path)) {
                    ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "cannot_locate_report_file", locale));
                }
            } catch (IOException e) {
                e.printStackTrace();
                return ServiceUtil.returnError(e.getMessage());
            }
        }

        // TODO : factoriser ce code qui ressort plusieurs fois dans un worker (je savais pas qu'on pouvait faire des workers là :D) !
        List<GenericValue> listContentRpt = null;
        try {
            for(String contentId:listContentId){ // serait ptet plus propre avec utilisation directe du delegator pour s'occuper de la liste en une seule requête par table, mais bouton pas forcément destiné à rester donc au plus simple
                delegator.removeByAnd("ContentAttribute", UtilMisc.toMap("contentId", contentId));
                listContentRpt = delegator.findList("ContentAssoc", EntityCondition.makeCondition("contentId", EntityOperator.EQUALS, contentId), UtilMisc.toSet("contentIdTo"), null, null, false);
                String contentIdRpt = listContentRpt.get(0).getString("contentIdTo");
                dispatcher.runSync("removeContentAndRelated", UtilMisc.toMap("contentId", contentId, "userLogin", userLogin, "locale", locale));
                dispatcher.runSync("removeContentAndRelated", UtilMisc.toMap("contentId", contentIdRpt, "userLogin", userLogin, "locale", locale));
            }
        } catch (GenericServiceException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        return ServiceUtil.returnSuccess(UtilProperties.getMessage(resource, "reports_successfully_deleted", locale));
    }

    // me demande si j'aurais pas dû faire un seul service de suppression avec listContentId en optionnel... 
    public static Map<String, Object> deleteOneReport(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String contentId = (String) context.get("contentId");

        List<GenericValue> listContentRpt = null;
        List<GenericValue> listRptDesignFileGV = null;
        String contentIdRpt;
        try {
            listContentRpt = delegator.findList("ContentAssoc", EntityCondition.makeCondition("contentId", EntityOperator.EQUALS, contentId), UtilMisc.toSet("contentIdTo"), null, null, false);
            contentIdRpt = listContentRpt.get(0).getString("contentIdTo");
            List<EntityExpr> listConditions = UtilMisc.toList(EntityCondition.makeCondition("contentTypeId", EntityOperator.EQUALS, "RPTDESIGN"), EntityCondition.makeCondition("contentId", EntityOperator.EQUALS, contentIdRpt));
            EntityConditionList<EntityExpr> ecl = EntityCondition.makeCondition(listConditions, EntityOperator.AND);
            listRptDesignFileGV = delegator.findList("ContentDataResourceView", ecl, UtilMisc.toSet("drObjectInfo"), null, null, false);
        } catch (GenericEntityException e1) {
            e1.printStackTrace();
            return ServiceUtil.returnError(e1.getMessage());
        }
        if(listRptDesignFileGV.size() > 1){
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "unexpected_number_reports_to_delete", locale));
        }
        List<String> listRptDesignFile = EntityUtil.getFieldListFromEntityList(listRptDesignFileGV, "drObjectInfo", false);
        String rptfileName = listRptDesignFile.get(0);
        Path path = Paths.get(rptfileName);
        try {
            if(!Files.deleteIfExists(path)){
                ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "cannot_locate_report_file", locale));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        try {
            delegator.removeByAnd("ContentAttribute", UtilMisc.toMap("contentId", contentId));
//            contentRpt = delegator.findOne("ContentAssoc", false, UtilMisc.toMap("contentId", contentId));
            dispatcher.runSync("removeContentAndRelated", UtilMisc.toMap("contentId", contentId, "userLogin", userLogin, "locale", locale));
            dispatcher.runSync("removeContentAndRelated", UtilMisc.toMap("contentId", contentIdRpt, "userLogin", userLogin, "locale", locale));
        } catch (GenericServiceException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        return ServiceUtil.returnSuccess(UtilProperties.getMessage(resource, "report_successfully_deleted", locale));
    }
    
    public static Map<String, Object> uploadRptDesign(DispatchContext dctx, Map<String, ? extends Object> context) {
        String dataResourceId = (String) context.get("dataResourceIdRpt");
        Locale locale = (Locale) context.get("locale");
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = null;
        List<String> listSuccessMessage = new ArrayList<String>();
        
        // the idea is to allow only design to be uploaded. We use the stored file and add the new design from the uploaded file within.
        DesignConfig config = new DesignConfig();

        IDesignEngine engine = null;

        try{
            Platform.startup();
            IDesignEngineFactory factory = (IDesignEngineFactory) Platform.createFactoryObject(IDesignEngineFactory.EXTENSION_DESIGN_ENGINE_FACTORY);
            engine = factory.createDesignEngine(config);
        }catch (Exception e){
            e.printStackTrace();
        }
        SessionHandle session = engine.newSessionHandle(ULocale.forLocale(locale));

        // get old file to restore dataset and datasource
        ByteBuffer newRptDesignBytes = (ByteBuffer) context.get("uploadRptDesign");
        if (newRptDesignBytes == null) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource_error, "cannot_find_uploaded_file", locale));
        }

        // check file name
        String uploadedFilename = (String) context.get("_uploadRptDesign_fileName");
        GenericValue dataResource;
        try {
            dataResource = delegator.findOne("DataResource", false, UtilMisc.toMap("dataResourceId", dataResourceId));
        } catch (GenericEntityException e1) {
            e1.printStackTrace();
            return ServiceUtil.returnError(e1.getMessage());
        }
        String rptDesignName = dataResource.getString("objectInfo");

        if(!uploadedFilename.equals(rptDesignName.substring(rptDesignName.lastIndexOf('/')+1))){
            listSuccessMessage.add(UtilProperties.getMessage(resource, "file_name_consistency_warning", locale));
        }
        
        // start Birt API platfrom
        try {
            Platform.startup();
        } catch (BirtException e) {
            e.printStackTrace();
            return ServiceUtil.returnError("Cannot start Birt platform");
        }
        
        // get database design
        ReportDesignHandle designStored;
        try {
            designStored = session.openDesign(rptDesignName);
        } catch (DesignFileException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }

        // check if design stored already has a body and delete it to avoid conflicts (taking into account only newly designed body)
        if(UtilValidate.isNotEmpty(designStored.getBody())) {
            SlotHandle bodyStored = designStored.getBody();

            Iterator<DesignElementHandle> iter = bodyStored.iterator();
            while(iter.hasNext()){
                try {
                    iter.remove();
                } catch (Exception e) {
                    e.printStackTrace();
                    return ServiceUtil.returnError(e.getMessage());
                }
            }
        }

        // NEED TO COPY STYLES, BODY, MASTERPAGE AND CUBES; existing elements (in case I missed one):
        //[styles, parameters, dataSources, dataSets, pageSetup, components, body, scratchPad, templateParameterDefinitions, cubes, themes]
        // get user design
        String nameTempRpt = rptDesignName.substring(0, rptDesignName.lastIndexOf('.')).concat("_TEMP_.rptdesign");
        File file = new File(nameTempRpt);
        RandomAccessFile out;
        ReportDesignHandle designFromUser;
        try {
            out = new RandomAccessFile(file, "rw");
            out.write(newRptDesignBytes.array());
            out.close();
            designFromUser = session.openDesign(nameTempRpt);
            // user file is deleted straight away to prevent the use of the report as script entry (security)
            Path path = Paths.get(nameTempRpt);
            Files.deleteIfExists(path);
        } catch (Exception e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }

        //copy cube
        SlotHandle cubesFromUser = designFromUser.getCubes();

        Iterator<DesignElementHandle> iterCube = cubesFromUser.iterator();

        while(iterCube.hasNext()){
            DesignElementHandle item = (DesignElementHandle) iterCube.next();
            DesignElementHandle copy = item.copy().getHandle(item.getModule());
            try {
                designStored.getCubes().add(copy);
            } catch (Exception e) {
                e.printStackTrace();
                return ServiceUtil.returnError(e.getMessage());
            }
        }

        // copy body
        SlotHandle bodyFromUser = designFromUser.getBody();

        Iterator<DesignElementHandle> iter = bodyFromUser.iterator();

        while(iter.hasNext()){
            DesignElementHandle item = (DesignElementHandle) iter.next();
            DesignElementHandle copy = item.copy().getHandle(item.getModule());
            try {
                designStored.getBody().add(copy);
            } catch (Exception e) {
                e.printStackTrace();
                return ServiceUtil.returnError(e.getMessage());
            }
        }

        // deleting simple master page from design stored
        try {
            List<DesignElementHandle> listMasterPagesStored = designStored.getMasterPages().getContents();
            for(Object masterPage : listMasterPagesStored) {
                if(masterPage instanceof SimpleMasterPageHandle) {
                    designStored.getMasterPages().drop((DesignElementHandle) masterPage);
                }
            }

            // adding simple master page => tous ces casts et autres instanceof... c'est laid, mais c'est tellement galère que quand je trouve une solution qui marche... :s
            List<DesignElementHandle> listMasterPages = designFromUser.getMasterPages().getContents();
            for(DesignElementHandle masterPage : listMasterPages) {
                if(masterPage instanceof SimpleMasterPageHandle) {
                        designStored.getMasterPages().add((SimpleMasterPage) ((SimpleMasterPageHandle) masterPage).copy());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }

        // page variables
        List<VariableElementHandle> pageVariablesUser = designFromUser.getPageVariables();
        for(VariableElementHandle pageVariable : pageVariablesUser) {
            try {
                designStored.setPageVariable(pageVariable.getName(), pageVariable.getPropertyBindingExpression(pageVariable.getName()));
            } catch (SemanticException e) {
                e.printStackTrace();
                return ServiceUtil.returnError(e.getMessage());
            }
        }

        // copy styles
        SlotHandle stylesFromUser = designFromUser.getStyles();
        SlotHandle stylesStored = designStored.getStyles();
        
        // getting style names from stored report
        List<String> listStyleNames = new ArrayList<String>();
        Iterator<DesignElementHandle> iterStored = stylesStored.iterator();
        while(iterStored.hasNext()){
            DesignElementHandle item = (DesignElementHandle) iterStored.next();
            listStyleNames.add(item.getName());
        }

        Iterator<DesignElementHandle> iterUser = stylesFromUser.iterator();

        // adding to styles those which are not already present
        while(iterUser.hasNext()){
            DesignElementHandle item = (DesignElementHandle) iterUser.next();
            if(!listStyleNames.contains(item.getName())){
                DesignElementHandle copy = item.copy().getHandle(item.getModule());
                try {
                    designStored.getStyles().add(copy);
                } catch (Exception e) {
                    e.printStackTrace();
                    return ServiceUtil.returnError(e.getMessage());
                }
            }
        }

        try {
            designStored.saveAs(rptDesignName);
        } catch (IOException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        designFromUser.close();
        designStored.close();
        Debug.log("####### Design uploaded: ".concat(rptDesignName));
        
        // should we as a secondary safety precaution delete any file finishing with _TEMP_.rptdesign?
        listSuccessMessage.add(UtilProperties.getMessage(resource, "report_file_successfully_uploaded", locale));
        result = ServiceUtil.returnSuccess(listSuccessMessage);
        return result;
    }

}