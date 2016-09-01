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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.notification.email.EmailNotificationChannel;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.sonar.core.permission.GlobalPermissions.SYSTEM_ADMIN;

public class TestActionTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  EmailNotificationChannel emailNotificationChannel = mock(EmailNotificationChannel.class);

  WsActionTester ws = new WsActionTester(new TestAction(userSession, emailNotificationChannel));

  @Test
  public void send_test_email() throws Exception {
    setUserAsSystemAdmin();

    executeRequest("john@doo.com", "Test Message from SonarQube", "This is a test message from SonarQube at http://localhost:9000");

    verify(emailNotificationChannel).sendTestEmail("john@doo.com", "Test Message from SonarQube", "This is a test message from SonarQube at http://localhost:9000");
  }

  @Test
  public void does_not_fail_when_subject_param_is_missing() throws Exception {
    setUserAsSystemAdmin();

    executeRequest("john@doo.com", null, "This is a test message from SonarQube at http://localhost:9000");

    verify(emailNotificationChannel).sendTestEmail("john@doo.com", null, "This is a test message from SonarQube at http://localhost:9000");
  }

  @Test
  public void fail_when_to_param_is_missing() throws Exception {
    setUserAsSystemAdmin();
    expectedException.expect(IllegalArgumentException.class);

    executeRequest(null, "Test Message from SonarQube", "This is a test message from SonarQube at http://localhost:9000");
  }

  @Test
  public void fail_when_message_param_is_missing() throws Exception {
    setUserAsSystemAdmin();
    expectedException.expect(IllegalArgumentException.class);

    executeRequest("john@doo.com", "Test Message from SonarQube", null);
  }

  @Test
  public void fail_when_insufficient_privileges() {
    userSession.anonymous().setGlobalPermissions(GlobalPermissions.QUALITY_PROFILE_ADMIN);
    expectedException.expect(ForbiddenException.class);

    ws.newRequest().execute();
  }

  @Test
  public void test_ws_definition() {
    WebService.Action action = ws.getDef();
    assertThat(action).isNotNull();
    assertThat(action.isInternal()).isTrue();
    assertThat(action.isPost()).isTrue();
    assertThat(action.responseExampleAsString()).isNull();
    assertThat(action.params()).hasSize(3);
  }

  private void executeRequest(@Nullable String to, @Nullable String subject, @Nullable String message) {
    TestRequest request = ws.newRequest();
    if (to != null) {
      request.setParam("to", to);
    }
    if (subject != null) {
      request.setParam("subject", subject);
    }
    if (message != null) {
      request.setParam("message", message);
    }
    request.execute();
  }

  private void setUserAsSystemAdmin() {
    userSession.login("admin").setGlobalPermissions(SYSTEM_ADMIN);
  }

}
