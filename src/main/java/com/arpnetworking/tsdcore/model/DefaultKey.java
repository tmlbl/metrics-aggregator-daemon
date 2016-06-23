/**
 * Copyright 2016 Ville Koskela
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.tsdcore.model;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;

import java.util.Objects;

/**
 * Default implementation of the <code>Key</code> interface.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class DefaultKey implements Key {

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutableMap<String, String> getParameters() {
        return _dimensions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCluster() {
        return _dimensions.get(CLUSTER_DIMENSION_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getService() {
        return _dimensions.get(SERVICE_DIMENSION_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHost() {
        return _dimensions.get(HOST_DIMENSION_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof DefaultKey)) {
            return false;
        }

        final DefaultKey otherKey = (DefaultKey) other;
        return Objects.equals(getParameters(), otherKey.getParameters());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(_dimensions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("Dimensions", _dimensions)
                .toString();
    }

    /**
     * Public constructor.
     *
     * @param dimensions The dimension key-value pairs.
     */
    public DefaultKey(final ImmutableMap<String, String> dimensions) {
        _dimensions = dimensions;
    }

    private final ImmutableMap<String, String> _dimensions;
}
