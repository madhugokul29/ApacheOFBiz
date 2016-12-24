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
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.collections4.MapUtils;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.template.FreeMarkerWorker;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.security.Security;
import org.apache.ofbiz.service.GenericServiceException;
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
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;

public final class BirtUtil {

    public final static String module = BirtUtil.class.getName();

    private final static HTMLServerImageHandler imageHandler = new HTMLServerImageHandler();
    private final static Map<String, String> entityFieldTypeBirtTypeMap = MapUtils.unmodifiableMap(UtilMisc.toMap(
            "id", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "url", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "name", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "id-ne", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "value", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "email", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "comment", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "id-long",DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "id-vlong", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "very-long", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "indicator", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "very-short", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "tel-number", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "id-long-ne", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "id-vlong-ne", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "description", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "long-varchar", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "short-varchar", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "credit-card-date", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "credit-card-number", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "date-time", DesignChoiceConstants.COLUMN_DATA_TYPE_DATETIME,
            "date", DesignChoiceConstants.COLUMN_DATA_TYPE_DATE,
            "time", DesignChoiceConstants.COLUMN_DATA_TYPE_TIME,
            "currency-amount", DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL,
            "currency-precise", DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL,
            "fixed-point", DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL,
            "floating-point", DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL,
            "numeric", DesignChoiceConstants.COLUMN_DATA_TYPE_INTEGER,
            "object", DesignChoiceConstants.COLUMN_DATA_TYPE_JAVA_OBJECT,
            "blob", DesignChoiceConstants.COLUMN_DATA_TYPE_BLOB));

    private final static Map<String, String> entityFieldTypeBirtParameterTypeMap = MapUtils.unmodifiableMap(UtilMisc.toMap(
            "id", DesignChoiceConstants.PARAM_TYPE_STRING,
            "url", DesignChoiceConstants.PARAM_TYPE_STRING,
            "name", DesignChoiceConstants.PARAM_TYPE_STRING,
            "id-ne", DesignChoiceConstants.PARAM_TYPE_STRING,
            "value", DesignChoiceConstants.PARAM_TYPE_STRING,
            "email", DesignChoiceConstants.PARAM_TYPE_STRING,
            "comment", DesignChoiceConstants.PARAM_TYPE_STRING,
            "id-long",DesignChoiceConstants.PARAM_TYPE_STRING,
            "id-vlong", DesignChoiceConstants.PARAM_TYPE_STRING,
            "very-long", DesignChoiceConstants.PARAM_TYPE_STRING,
            "indicator", DesignChoiceConstants.PARAM_TYPE_STRING,
            "very-short", DesignChoiceConstants.PARAM_TYPE_STRING,
            "tel-number", DesignChoiceConstants.PARAM_TYPE_STRING,
            "id-long-ne", DesignChoiceConstants.PARAM_TYPE_STRING,
            "id-vlong-ne", DesignChoiceConstants.PARAM_TYPE_STRING,
            "description", DesignChoiceConstants.PARAM_TYPE_STRING,
            "long-varchar", DesignChoiceConstants.PARAM_TYPE_STRING,
            "short-varchar", DesignChoiceConstants.PARAM_TYPE_STRING,
            "credit-card-date", DesignChoiceConstants.PARAM_TYPE_STRING,
            "credit-card-number", DesignChoiceConstants.PARAM_TYPE_STRING,
            "date-time", DesignChoiceConstants.PARAM_TYPE_DATETIME,
            "date", DesignChoiceConstants.PARAM_TYPE_DATE,
            "time", DesignChoiceConstants.PARAM_TYPE_TIME,
            "currency-amount", DesignChoiceConstants.PARAM_TYPE_DECIMAL,
            "currency-precise", DesignChoiceConstants.PARAM_TYPE_DECIMAL,
            "fixed-point", DesignChoiceConstants.PARAM_TYPE_DECIMAL,
            "floating-point", DesignChoiceConstants.PARAM_TYPE_DECIMAL,
            "numeric", DesignChoiceConstants.PARAM_TYPE_INTEGER,
            "object", DesignChoiceConstants.PARAM_TYPE_JAVA_OBJECT,
            "blob", DesignChoiceConstants.PARAM_TYPE_JAVA_OBJECT));

    private BirtUtil() {}

    public static String convertFieldTypeToBirtType(String entityFieldType) {
        if (UtilValidate.isEmpty(entityFieldType)) {
            return null;
        }
        return entityFieldTypeBirtTypeMap.get(entityFieldType.toLowerCase());
    }

    public static String convertFieldTypeToBirtParameterType(String entityFieldType) {
        if (UtilValidate.isEmpty(entityFieldType)) {
            return null;
        }
        return entityFieldTypeBirtParameterTypeMap.get(entityFieldType.toLowerCase());
    }


}