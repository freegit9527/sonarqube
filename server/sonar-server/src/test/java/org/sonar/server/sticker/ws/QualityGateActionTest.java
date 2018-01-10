/*
 * SonarQube
 * Copyright (C) 2009-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonar.server.sticker.ws;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ResourceTypesRule;
import org.sonar.db.metric.MetricDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class QualityGateActionTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public DbTester db = DbTester.create();

  private ResourceTypesRule resourceTypes = new ResourceTypesRule().setRootQualifiers(Qualifiers.PROJECT);

  private WsActionTester ws = new WsActionTester(new QualityGateAction(userSession, db.getDbClient(), new ComponentFinder(db.getDbClient(), resourceTypes)));

  @Test
  public void generate_passing_svg() {
    ComponentDto project = db.components().insertPublicProject();
    userSession.registerComponents(project);
    MetricDto qualityGateMetric = db.measures().insertMetric(m -> m.setKey(CoreMetrics.ALERT_STATUS_KEY));
    db.measures().insertLiveMeasure(project, qualityGateMetric, m -> m.setData("OK"));

    String response = ws.newRequest()
      .setParam("component", project.getKey())
      .execute().getInput();

    assertResponseIsQualityGatePassing(response);
  }

  @Test
  public void test_definition() {
    WebService.Action def = ws.getDef();
    assertThat(def.key()).isEqualTo("quality_gate");
    assertThat(def.isInternal()).isFalse();
    assertThat(def.isPost()).isFalse();
    assertThat(def.since()).isNull();
    assertThat(def.responseExampleAsString()).isNotEmpty();

    assertThat(def.params())
      .extracting(Param::key, Param::isRequired)
      .containsExactlyInAnyOrder(
        tuple("component", true),
        tuple("type", false));
  }

  @Test
  public void test_example() {
    String response = ws.newRequest().execute().getInput();

    assertThat(response).isEqualTo(ws.getDef().responseExampleAsString());
  }

  private void assertResponseIsQualityGatePassing(String response) {
    try {
      assertThat(response).isEqualTo(IOUtils.toString(getClass().getResource("quality_gate-passing.svg").toURI().toURL(), UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
