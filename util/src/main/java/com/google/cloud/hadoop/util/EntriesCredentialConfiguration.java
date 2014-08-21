/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.hadoop.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * CredentialConfiguration based on configuration objects that implement our Entries interface.
 *
 * When reading configuration this class makes use of a list of key prefixes that are each applied
 * to key suffixes to create a complete configuration key. There is a base prefix of 'google.cloud.'
 * that is included by the builder for each EntriesCredentialConfiguration created. When
 * constructing, other prefixes can be specified. Prefixes specified later can be used to override
 * the values of previously set values. In this way a set of global credentials can be specified
 * for most connectors with an override specified for any connectors that need different
 * credentials.
 */
public class EntriesCredentialConfiguration extends CredentialConfiguration {

  /** The interface to interact with the configuration object. */
  public interface Entries {

    /** Returns the value of an entry. */
    String get(String key);

    /** Sets the value of an entry. */
    void set(String key, String value);

    /** Sets the value of an entry to a boolean value. */
    void setBoolean(String key, boolean value);
  }

  /**
   * All instances constructed using the builder will use "google.cloud" as the first
   * prefix checked. Other prefixes can be added and will override values in the google.cloud
   * prefix.
   */
  public static final String BASE_KEY_PREFIX = "google.cloud";
  /**
   * Key suffix used to disable service accounts. A value of 'false' will disable the use of
   * service accounts. The default is to use a service account.
   */
  public static final String ENABLE_SERVICE_ACCOUNTS_SUFFIX = ".auth.service.account.enable";
  /**
   * Key suffix used to control which email address is associated with the service account.
   */
  public static final String SERVICE_ACCOUNT_EMAIL_SUFFIX = ".auth.service.account.email";
  /**
   * Key suffix used to indicate the path to the service account p12 keyfile. If provided, triggers
   * private keyfile service account authentication. The file will be required to be present on all
   * nodes and at the same location on all nodes.
   */
  public static final String SERVICE_ACCOUNT_KEYFILE_SUFFIX = ".auth.service.account.keyfile";
  /**
   * For OAuth-based Installed App authentication, the key suffix specifying the client ID for
   * the credentials.
   */
  public static final String CLIENT_ID_SUFFIX = ".auth.client.id";
  /**
   * For OAuth-based Installed App authentication, the key suffix specifying the client secret
   * for the credentials.
   */
  public static final String CLIENT_SECRET_SUFFIX = ".auth.client.secret";
  /**
   * For OAuth-based Installed App authentication, the key suffix specifying the file containing
   * credentials (JWT).
   */
  public static final String OAUTH_CLIENT_FILE_SUFFIX = ".auth.client.file";
  /**
   * For unit-testing, the key suffix allowing null to be returned from credential creation
   * instead of logging an error and aborting.
   */
  public static final String ENABLE_NULL_CREDENTIAL_SUFFIX = ".auth.null.enable";

  /**
   * Builder for constructing CredentialConfiguration instances.
   */
  public abstract static class Builder<B extends Builder<B, T>,
                                       T extends EntriesCredentialConfiguration> {
    protected List<String> prefixes = new ArrayList<>();
    private Entries configuration;
    private CredentialFactory credentialFactory = new CredentialFactory();

    public Builder () {
      prefixes.add(BASE_KEY_PREFIX);
    }

    /**
     * Return "this" of the appropriate Builder type.
     * Builder subclass must override to return "this".
     */
    protected abstract B self();

    /**
     * Return an instance of the concrete type specified as the second template argument of this
     * builder, optionally performing any type-specific initialization before this builder
     * finishes off the rest of the build() method using methods defined within
     * the EntriesCredentialConfiguration base class.
     * Subclasses must override to return a constructed instance of the concrete class being built.
     */
    protected abstract T beginBuild();

    /**
     * Return the fully-assembled concrete object for which this is a builder.
     */
    public T build() {
      T concreteCredentialConfiguration = beginBuild();
      if (configuration != null) {
        concreteCredentialConfiguration.setConfiguration(configuration);
      }
      if (credentialFactory != null) {
        concreteCredentialConfiguration.setCredentialFactory(credentialFactory);
      }
      return concreteCredentialConfiguration;
    }

    @VisibleForTesting
    B withCredentialFactory(CredentialFactory credentialFactory) {
      this.credentialFactory = credentialFactory;
      return self();
    }

    public B withConfiguration(Entries configuration) {
      this.configuration = configuration;
      return self();
    }

    public B withOverridePrefix(String prefix) {
      this.prefixes.add(prefix);
      return self();
    }
  }

  /**
   * A builder for use without a subclasses of EntriesCredentialConfiguration.
   */
  public static class EntriesBuilder
      extends Builder<EntriesBuilder, EntriesCredentialConfiguration> {
    @Override
    public EntriesBuilder self() {
      return this;
    }

    @Override
    protected EntriesCredentialConfiguration beginBuild() {
      return new EntriesCredentialConfiguration(prefixes);
    }
  }

  /**
   * Create a builder for this class.
   */
  public static EntriesBuilder newEntriesBuilder() {
    return new EntriesBuilder();
  }

  private static Optional<Boolean> maybeGetBoolean(Entries config, String key) {
    String value = config.get(key);
    if (value == null) {
      return Optional.absent();
    }
    return Optional.of(Boolean.valueOf(value));
  }

  private final List<String> prefixes;
  public EntriesCredentialConfiguration(List<String> prefixes) {
    this.prefixes = ImmutableList.copyOf(prefixes);
  }

  /** Gets our parameters and fills it into the specified configuration.
   * Typically the passed-in configuration is an empty
   * instance of a configuration object that extends Entries.
   */
  public void getConfigurationInto(Entries configuration) {
    for (String prefix : prefixes) {
      configuration.setBoolean(prefix + ENABLE_SERVICE_ACCOUNTS_SUFFIX, isServiceAccountEnabled());

      if (getServiceAccountEmail() != null) {
        configuration.set(prefix + SERVICE_ACCOUNT_EMAIL_SUFFIX, getServiceAccountEmail());
      }
      if (getServiceAccountKeyFile() != null) {
        configuration.set(prefix + SERVICE_ACCOUNT_KEYFILE_SUFFIX, getServiceAccountKeyFile());
      }
      if (getClientId() != null) {
        configuration.set(prefix + CLIENT_ID_SUFFIX, getClientId());
      }
      if (getClientSecret() != null) {
        configuration.set(prefix + CLIENT_SECRET_SUFFIX, getClientSecret());
      }
      if (getOAuthCredentialFile() != null) {
        configuration.set(prefix + OAUTH_CLIENT_FILE_SUFFIX, getOAuthCredentialFile());
      }

      configuration.setBoolean(prefix + ENABLE_NULL_CREDENTIAL_SUFFIX, isNullCredentialEnabled());
    }
  }

  /**
   * Load configuration values from the provided configuration source. For any key that does not
   * have a corresponding value in the configuration, no changes will be made to the state of this
   * object.
   */
  public void setConfiguration(Entries entries) {
    for (String prefix : prefixes) {
      Optional<Boolean> enableServiceAccounts =
          maybeGetBoolean(entries, prefix + ENABLE_SERVICE_ACCOUNTS_SUFFIX);

      if (enableServiceAccounts.isPresent()) {
        setEnableServiceAccounts(enableServiceAccounts.get());
      }

      String serviceEmailAccount = entries.get(prefix + SERVICE_ACCOUNT_EMAIL_SUFFIX);
      if (serviceEmailAccount != null) {
        setServiceAccountEmail(serviceEmailAccount);
      }

      String serviceAccountKeyFile = entries.get(prefix + SERVICE_ACCOUNT_KEYFILE_SUFFIX);
      if (serviceAccountKeyFile != null) {
        setServiceAccountKeyFile(serviceAccountKeyFile);
      }

      String clientId = entries.get(prefix + CLIENT_ID_SUFFIX);
      if (clientId != null) {
        setClientId(clientId);
      }

      String clientSecret = entries.get(prefix + CLIENT_SECRET_SUFFIX);
      if (clientSecret != null) {
        setClientSecret(clientSecret);
      }

      String oAuthCredentialPath = entries.get(prefix + OAUTH_CLIENT_FILE_SUFFIX);
      if (oAuthCredentialPath != null) {
        setOAuthCredentialFile(oAuthCredentialPath);
      }

      Optional<Boolean> enableNullCredential = maybeGetBoolean(entries,
          prefix + ENABLE_NULL_CREDENTIAL_SUFFIX);
      if (enableNullCredential.isPresent()) {
        setNullCredentialEnabled(enableNullCredential.get());
      }
    }
  }
}