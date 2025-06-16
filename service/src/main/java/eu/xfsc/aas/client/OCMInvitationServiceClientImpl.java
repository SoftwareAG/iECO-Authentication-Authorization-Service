/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.xfsc.aas.client;

import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OCMInvitationServiceClientImpl implements InvitationServiceClient {

	@Value("${aas.tsa.enabled}")
    private Boolean tsaEnabled;

    @Override
    public String getMobileInvitationUrl(String uri) {
		log.debug("getMobileInvitationUrl.enter; got uri: {}", uri);
		return uri;
	}
}
