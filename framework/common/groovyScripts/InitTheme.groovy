/*
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
 */
import org.apache.ofbiz.base.util.UtilProperties

if (!context.userPreferences) {
    Map userPreferencesResult = run service: 'getUserPreferenceGroup', with: ['userPrefGroupTypeId': 'GLOBAL_PREFERENCES']
    context.userPreferences = userPreferencesResult.userPrefMap
}

if (!context.generalProperties) {
    context.generalProperties = UtilProperties.getResourceBundleMap('general', context.locale, context);
}

if (!context.visualThemeId) {
    context.visualThemeId = userPreferences.VISUAL_THEME ? userPreferences.VISUAL_THEME : generalProperties.VISUAL_THEME
}

if (!context.layoutSettings) {
    Map themeResourcesResult = run service: 'getVisualThemeResources', with: ['visualThemeId': context.visualThemeId]
    context.layoutSettings = themeResourcesResult.themeResources;
}