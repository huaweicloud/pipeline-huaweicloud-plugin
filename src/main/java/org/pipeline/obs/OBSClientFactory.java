/*
 * -
 * #%L
 * Pipeline: OBS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.pipeline.obs;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.huawei.openstack4j.api.OSClient.OSClientAKSK;
import com.huawei.openstack4j.api.types.ServiceType;
import com.huawei.openstack4j.core.transport.Config;
import com.huawei.openstack4j.model.common.Identifier;
import com.huawei.openstack4j.model.identity.v3.Project;
import com.huawei.openstack4j.openstack.OSFactory;
import com.huawei.openstack4j.openstack.identity.internal.OverridableEndpointURLResolver;
import com.huawei.openstack4j.api.types.ServiceType;
import com.obs.services.IObsCredentialsProvider;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import hudson.EnvVars;

public class OBSClientFactory {

	static final String OBS_PROFILE = "OBS_PROFILE";
	static final String OBS_DEFAULT_PROFILE = "OBS_DEFAULT_PROFILE";
	static final String OBS_ACCESS_KEY_ID = "OBS_ACCESS_KEY_ID";
	static final String OBS_SECRET_ACCESS_KEY = "OBS_SECRET_ACCESS_KEY";
	static final String OBS_SESSION_TOKEN = "OBS_SESSION_TOKEN";
	static final String OBS_DEFAULT_REGION = "OBS_DEFAULT_REGION";
	static final String OBS_REGION = "OBS_REGION";
	static final String OBS_ENDPOINT_URL = "OBS_ENDPOINT_URL";

	private OBSClientFactory() {
		//
	}

	public static OSClientAKSK createOscClient(StepContext context){
		EnvVars vars;
		try {
			vars = context.get(EnvVars.class);
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
		String region = vars.get(OBS_REGION);
		String endpointURL = vars.get(OBS_ENDPOINT_URL);
		String cloudDomainName =
			getCloudDomainName(endpointURL, region);
		String projectID = getProjectID(vars, cloudDomainName);

		OverridableEndpointURLResolver endpointResolver =
			new OverridableEndpointURLResolver();
		endpointResolver.addOverrideEndpoint(ServiceType.FGS2_0,
			"https://functiongraph."+ region + ".myhuaweicloud.com/v2/"
				+ projectID);

		Config config = Config.newConfig().
			withEndpointURLResolver(endpointResolver).withLanguage("zh-cn")
			.withSSLVerificationDisabled();

		OSClientAKSK osclient = OSFactory.builderAKSK().withConfig(config).
			credentials(vars.get(OBS_ACCESS_KEY_ID),
				vars.get(OBS_SECRET_ACCESS_KEY), region,
				cloudDomainName).authenticate();
		return osclient;
	}

	private static String getProjectID(final EnvVars vars, String cloudDomainName) {
		OSClientAKSK osclient =
			OSFactory.builderAKSK().credentials(vars.get(OBS_ACCESS_KEY_ID),
				vars.get(OBS_SECRET_ACCESS_KEY), vars.get(OBS_REGION),
				cloudDomainName).authenticate();

		String projectID = "";
		Map filteringParams = new HashMap();
		filteringParams.put("name", vars.get(OBS_REGION));
		List<? extends Project> projectList =
			osclient.identity().projects().listByObject(filteringParams);
		for(Project project : projectList){
			projectID = project.getId();
		}
		return projectID;
	}

	private static String getCloudDomainName(String url, String region) {
		String[] vs = url.split(region + "[.]");
		if (vs.length > 0) {
			return vs[1];
		}
		return "";
	}

	public static ObsClient createHuaweiObsClient(EnvVars vars) {
		ObsClient obs = null;
		obs = new ObsClient(vars.get(OBS_ACCESS_KEY_ID),
			vars.get(OBS_SECRET_ACCESS_KEY),
			vars.get(OBS_ENDPOINT_URL));
		return obs;
	}
}
