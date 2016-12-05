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
package org.apache.ofbiz.widget.renderer.macro;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilCodec;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.collections.MapStack;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.webapp.view.AbstractViewHandler;
import org.apache.ofbiz.webapp.view.ViewHandlerException;
import org.apache.ofbiz.widget.renderer.FormStringRenderer;
import org.apache.ofbiz.widget.renderer.MenuStringRenderer;
import org.apache.ofbiz.widget.renderer.ScreenRenderer;
import org.apache.ofbiz.widget.renderer.ScreenStringRenderer;
import org.apache.ofbiz.widget.renderer.TreeStringRenderer;
import org.xml.sax.SAXException;

import freemarker.template.TemplateException;
import freemarker.template.utility.StandardCompress;

public class MacroScreenViewHandler extends AbstractViewHandler {

    public static final String module = MacroScreenViewHandler.class.getName();
    public static final String defaultWidgetConfigProperties = "commonWidget";
    public String visualThemeWidgetConfigProperties = null;

    protected ServletContext servletContext = null;

    public void init(ServletContext context) throws ViewHandlerException {
        this.servletContext = context;
    }

    private ScreenStringRenderer loadRenderers(HttpServletRequest request, HttpServletResponse response,
            Map<String, Object> context, Writer writer) throws GeneralException, TemplateException, IOException {

        //resolve default value for macro lib
        String propertyNameScreenRenderer = getName() + ".screenrenderer";
        String propertyNameFormRenderer = getName() + ".formrenderer";
        String propertyNameTreeRenderer = getName() + ".treerenderer";
        String propertyNameMenuRenderer = getName() + ".menurenderer";
        String screenMacroLibraryPath = UtilProperties.getPropertyValue(defaultWidgetConfigProperties, propertyNameScreenRenderer);
        String formMacroLibraryPath = UtilProperties.getPropertyValue(defaultWidgetConfigProperties, propertyNameFormRenderer);
        String treeMacroLibraryPath = UtilProperties.getPropertyValue(defaultWidgetConfigProperties, propertyNameTreeRenderer);
        String menuMacroLibraryPath = UtilProperties.getPropertyValue(defaultWidgetConfigProperties, propertyNameMenuRenderer);

        //if theme have a specific property widget file, resolve the path
        if (visualThemeWidgetConfigProperties != null) {
            screenMacroLibraryPath = UtilProperties.getPropertyValue(visualThemeWidgetConfigProperties, propertyNameScreenRenderer, screenMacroLibraryPath);
            formMacroLibraryPath = UtilProperties.getPropertyValue(visualThemeWidgetConfigProperties, propertyNameFormRenderer, formMacroLibraryPath);
            treeMacroLibraryPath = UtilProperties.getPropertyValue(visualThemeWidgetConfigProperties, propertyNameTreeRenderer, treeMacroLibraryPath);
            menuMacroLibraryPath = UtilProperties.getPropertyValue(visualThemeWidgetConfigProperties, propertyNameMenuRenderer, menuMacroLibraryPath);
        }

        ScreenStringRenderer screenStringRenderer = new MacroScreenRenderer(UtilProperties.getPropertyValue(defaultWidgetConfigProperties, getName()
                + ".name"), screenMacroLibraryPath);
        if (!formMacroLibraryPath.isEmpty()) {
            FormStringRenderer formStringRenderer = new MacroFormRenderer(formMacroLibraryPath, request, response);
            context.put("formStringRenderer", formStringRenderer);
        }
        if (!treeMacroLibraryPath.isEmpty()) {
            TreeStringRenderer treeStringRenderer = new MacroTreeRenderer(treeMacroLibraryPath, writer);
            context.put("treeStringRenderer", treeStringRenderer);
        }
        if (!menuMacroLibraryPath.isEmpty()) {
            MenuStringRenderer menuStringRenderer = new MacroMenuRenderer(menuMacroLibraryPath, request, response);
            context.put("menuStringRenderer", menuStringRenderer);
        }
        return screenStringRenderer;
    }

    public void render(String name, String page, String info, String contentType, String encoding, HttpServletRequest request, HttpServletResponse response) throws ViewHandlerException {
        try {
            MapStack<String> context = MapStack.create();
            ScreenRenderer.populateContextForRequest(context, null, request, response, servletContext);

            //Resolve the visual theme
            String visualThemeId = (String) context.get("visualThemeId");
            visualThemeWidgetConfigProperties = null;
            if (visualThemeId == null) {
                Map<String, Object> userPreferences = UtilGenerics.cast(context.get("userPreferences"));
                if (userPreferences != null) {
                    visualThemeId = (String) userPreferences.get("VISUAL_THEME");
                    if (visualThemeId != null) {
                        LocalDispatcher dispatcher = (LocalDispatcher) context.get("dispatcher");
                        Map<String, Object> serviceCtx = dispatcher.getDispatchContext().makeValidContext("getVisualThemeResources",
                                ModelService.IN_PARAM, context);
                        serviceCtx.put("visualThemeId", visualThemeId);
                        Map<String, Object> serviceResult = dispatcher.runSync("getVisualThemeResources", serviceCtx);
                        if (ServiceUtil.isSuccess(serviceResult)) {
                            Map<String, List<String>> themeResources = UtilGenerics.cast(serviceResult.get("themeResources"));
                            List<String> resourceList = UtilGenerics.cast(themeResources.get("VT_WIDGET_CONFIG"));
                            if (resourceList != null && ! resourceList.isEmpty()) {
                                visualThemeWidgetConfigProperties = resourceList.get(0);
                            }
                        }
                    }
                } else {
                    visualThemeId = "COMMON";
                }
            }
            Writer writer = response.getWriter();
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            // compress output if configured to do so
            if (UtilValidate.isEmpty(encoding)) {
                encoding = EntityUtilProperties.getPropertyValue(defaultWidgetConfigProperties, getName() + ".default.encoding", "none", delegator);
            }
            boolean compressOutput = "compressed".equals(encoding);
            if (!compressOutput) {
                compressOutput = "true".equals(EntityUtilProperties.getPropertyValue("widget", getName() + ".compress", delegator));
            }
            if (!compressOutput && this.servletContext != null) {
                compressOutput = "true".equals(this.servletContext.getAttribute("compressHTML"));
            }
            if (compressOutput) {
                // StandardCompress defaults to a 2k buffer. That could be increased
                // to speed up output.
                writer = new StandardCompress().getWriter(writer, null);
            }
            ScreenStringRenderer screenStringRenderer = loadRenderers(request, response, context, writer);
            ScreenRenderer screens = new ScreenRenderer(writer, context, screenStringRenderer);
            context.put("screens", screens);

            //resolve encoder
            String encoderName = UtilProperties.getPropertyValue(defaultWidgetConfigProperties, getName() + ".encoder");
            if (visualThemeWidgetConfigProperties != null) {
                encoderName = UtilProperties.getPropertyValue(visualThemeWidgetConfigProperties, getName() + ".encoder", encoderName);
            }
            context.put("simpleEncoder", UtilCodec.getEncoder(encoderName));
            screenStringRenderer.renderScreenBegin(writer, context);
            screens.render(page);
            screenStringRenderer.renderScreenEnd(writer, context);
            writer.flush();
        } catch (TemplateException e) {
            Debug.logError(e, "Error initializing screen renderer", module);
            throw new ViewHandlerException(e.getMessage());
        } catch (IOException e) {
            throw new ViewHandlerException("Error in the response writer/output stream: " + e.toString(), e);
        } catch (SAXException e) {
            throw new ViewHandlerException("XML Error rendering page: " + e.toString(), e);
        } catch (ParserConfigurationException e) {
            throw new ViewHandlerException("XML Error rendering page: " + e.toString(), e);
        } catch (GeneralException e) {
            throw new ViewHandlerException("Lower level error rendering page: " + e.toString(), e);
        }
    }
}
