/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.email.ws;

import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.property.PropertyDto;
import org.sonar.server.user.UserSession;

import static org.sonar.api.config.EmailSettings.FROM;
import static org.sonar.api.config.EmailSettings.FROM_DEFAULT;
import static org.sonar.api.config.EmailSettings.PREFIX;
import static org.sonar.api.config.EmailSettings.PREFIX_DEFAULT;
import static org.sonar.api.config.EmailSettings.SMTP_HOST;
import static org.sonar.api.config.EmailSettings.SMTP_PASSWORD;
import static org.sonar.api.config.EmailSettings.SMTP_PORT;
import static org.sonar.api.config.EmailSettings.SMTP_PORT_DEFAULT;
import static org.sonar.api.config.EmailSettings.SMTP_SECURE_CONNECTION;
import static org.sonar.api.config.EmailSettings.SMTP_USERNAME;

public class UpdateAction implements EmailsWsAction {

  private static final String PARAM_HOST = "host";
  private static final String PARAM_PORT = "port";
  private static final String PARAM_SECURE = "secure";
  private static final String PARAM_USERNAME = "username";
  private static final String PARAM_PASSWORD = "password";
  private static final String PARAM_FROM = "from";
  private static final String PARAM_PREFIX = "prefix";

  private static final String SSL_VALUE = "ssl";
  private static final String STARTTLS_VALUE = "starttls";

  private final DbClient dbClient;
  private final UserSession userSession;

  public UpdateAction(DbClient dbClient, UserSession userSession) {
    this.dbClient = dbClient;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction("update")
      .setDescription("Update email settings<br>" +
        "Requires 'Administer System' permission")
      .setSince("6.1")
      .setInternal(true)
      .setPost(true)
      .setHandler(this);

    action.createParam(PARAM_HOST)
      .setDescription("SMTP host. Leave blank to disable email sending.")
      .setExampleValue("smtp.gmail.com");

    action.createParam(PARAM_PORT)
      .setDescription("Port number to connect with SMTP server.")
      .setExampleValue(SMTP_PORT_DEFAULT);

    action.createParam(PARAM_SECURE)
      .setDescription("Whether to use secure connection and its type.")
      .setPossibleValues(SSL_VALUE, STARTTLS_VALUE)
      .setExampleValue(SSL_VALUE);

    action.createParam(PARAM_USERNAME)
      .setDescription("Username to use with authenticated SMTP.")
      .setExampleValue("my_username");

    action.createParam(PARAM_PASSWORD)
      .setDescription("Username to use with authenticated SMTP.")
      .setExampleValue("my_password");

    action.createParam(PARAM_FROM)
      .setDescription("Emails will come from this address. For example - \"noreply@sonarsource.com\". Note that server may ignore this setting (like does GMail).")
      .setExampleValue(FROM_DEFAULT);

    action.createParam(PARAM_PREFIX)
      .setDescription("Prefix will be prepended to all outgoing email subjects.")
      .setExampleValue(PREFIX_DEFAULT);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkPermission(GlobalPermissions.SYSTEM_ADMIN);

    DbSession dbSession = dbClient.openSession(false);
    try {
      save(dbSession, SMTP_HOST, request.param(PARAM_HOST));
      save(dbSession, SMTP_PORT, request.hasParam(PARAM_PORT) ? Integer.toString(request.paramAsInt(PARAM_PORT)) : null);
      save(dbSession, SMTP_SECURE_CONNECTION, request.param(PARAM_SECURE));
      save(dbSession, SMTP_USERNAME, request.param(PARAM_USERNAME));
      save(dbSession, SMTP_PASSWORD, request.param(PARAM_PASSWORD));
      save(dbSession, FROM, request.param(PARAM_FROM));
      save(dbSession, PREFIX, request.param(PARAM_PREFIX));
      dbSession.commit();
    } finally {
      dbClient.closeSession(dbSession);
    }
    response.noContent();
  }

  private void save(DbSession dbSession, String key, @Nullable String value) {
    if (value != null) {
      dbClient.propertiesDao().insertProperty(dbSession, new PropertyDto().setKey(key).setValue(value));
    } else {
      dbClient.propertiesDao().deleteGlobalProperty(key);
    }
  }

}
