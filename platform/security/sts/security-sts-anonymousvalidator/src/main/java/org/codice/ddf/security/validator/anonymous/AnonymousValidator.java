/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.security.validator.anonymous;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.cxf.sts.request.ReceivedToken;
import org.apache.cxf.sts.token.validator.TokenValidator;
import org.apache.cxf.sts.token.validator.TokenValidatorParameters;
import org.apache.cxf.sts.token.validator.TokenValidatorResponse;
import org.apache.cxf.ws.security.sts.provider.model.secext.BinarySecurityTokenType;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.codice.ddf.security.handler.api.AnonymousAuthenticationToken;
import org.codice.ddf.security.handler.api.BaseAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.security.principal.AnonymousPrincipal;

public class AnonymousValidator implements TokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnonymousValidator.class);

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile(
            "^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

    private static final Pattern IPV6_STD_PATTERN = Pattern
            .compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");

    private List<String> supportedRealm;

    private AnonymousAuthenticationToken getAnonTokenFromTarget(ReceivedToken validateTarget) {
        Object token = validateTarget.getToken();
        if ((token instanceof BinarySecurityTokenType)
                && AnonymousAuthenticationToken.ANONYMOUS_TOKEN_VALUE_TYPE
                .equals(((BinarySecurityTokenType) token).getValueType())) {
            String credential = ((BinarySecurityTokenType) token).getValue();
            try {
                BaseAuthenticationToken base = AnonymousAuthenticationToken.parse(credential, true);
                return new AnonymousAuthenticationToken(base.getRealm(),
                        AnonymousPrincipal.parseAddressFromName(base.getPrincipal().toString()));
            } catch (WSSecurityException e) {
                LOGGER.warn("Unable to parse {} from encodedToken.",
                        AnonymousAuthenticationToken.class.getSimpleName(), e);
                return null;
            }
        }
        return null;
    }

    private boolean validIpAddress(String address) {
        return IPV4_PATTERN.matcher(address).matches() || IPV6_STD_PATTERN.matcher(address)
                .matches() || IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches();
    }

    @Override
    public boolean canHandleToken(ReceivedToken validateTarget) {
        return canHandleToken(validateTarget, null);
    }

    @Override
    public boolean canHandleToken(ReceivedToken validateTarget, String realm) {
        AnonymousAuthenticationToken anonToken = getAnonTokenFromTarget(validateTarget);
        // currently realm is not being passed through (no RealmParser that determines the realm
        // based on the web context. So this just looks at the realm passed in the credentials.
        // This generic instance just looks for the default realms (DDF and Karaf)
        if (anonToken != null) {
            if (anonToken.getRealm() == null) {
                LOGGER.trace("No realm specified in request, canHandletoken = true");
                return true;
            } else {
                if (supportedRealm.contains(anonToken.getRealm()) || "*"
                        .equals(anonToken.getRealm())) {
                    LOGGER.trace("Realm '{}' recognized - canHandleToken = true",
                            anonToken.getRealm());
                    return true;
                } else {
                    LOGGER.trace("Realm '{}' unrecognized - canHandleToken = false",
                            anonToken.getRealm());
                }
            }
        }
        return false;
    }

    @Override
    public TokenValidatorResponse validateToken(TokenValidatorParameters tokenParameters) {
        TokenValidatorResponse response = new TokenValidatorResponse();
        ReceivedToken validateTarget = tokenParameters.getToken();
        validateTarget.setState(ReceivedToken.STATE.INVALID);

        AnonymousAuthenticationToken anonToken = getAnonTokenFromTarget(validateTarget);

        response.setToken(validateTarget);

        if (anonToken != null) {
            response.setPrincipal(new AnonymousPrincipal(anonToken.getIpAddress()));

            if (anonToken.getRealm() != null) {
                if ((supportedRealm.contains(anonToken.getRealm()) || "*"
                        .equals(anonToken.getRealm())) && anonToken.getCredentials()
                        .equals(AnonymousAuthenticationToken.ANONYMOUS_CREDENTIALS)
                        && validIpAddress(anonToken.getIpAddress())) {
                    validateTarget.setState(ReceivedToken.STATE.VALID);
                    validateTarget.setPrincipal(new AnonymousPrincipal(anonToken.getIpAddress()));
                }
            } else if (anonToken.getCredentials()
                    .equals(AnonymousAuthenticationToken.ANONYMOUS_CREDENTIALS) && validIpAddress(
                    anonToken.getIpAddress())) {
                validateTarget.setState(ReceivedToken.STATE.VALID);
                validateTarget.setPrincipal(new AnonymousPrincipal(anonToken.getIpAddress()));
            }
        }
        return response;
    }

    /**
     * Set the realm that this validator supports. This can be used to differentiate between
     * two instances of this validator where each contains a differnent token validator.
     *
     * @param supportedRealm string representing the realm supported by this validator
     */
    public void setSupportedRealm(List<String> supportedRealm) {
        this.supportedRealm = supportedRealm;
    }
}
