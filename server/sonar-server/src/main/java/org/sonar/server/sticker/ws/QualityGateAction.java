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

import com.google.common.io.Resources;
import java.util.Optional;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.NewAction;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.user.UserSession;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.apache.commons.io.IOUtils.copy;
import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;

public class QualityGateAction implements StickersWsAction {

  private static final String PARAM_COMPONENT = "component";
  private static final String PARAM_TYPE = "type";

  private final UserSession userSession;
  private final DbClient dbClient;
  private final ComponentFinder componentFinder;

  public QualityGateAction(UserSession userSession, DbClient dbClient, ComponentFinder componentFinder) {
    this.userSession = userSession;
    this.dbClient = dbClient;
    this.componentFinder = componentFinder;
  }

  @Override
  public void define(WebService.NewController controller) {
    NewAction action = controller.createAction("quality_gate")
      .setHandler(this)
      .setDescription("Generate badge for quality gate as an SVG.<br/>" +
        "Requires the 'Browse' permission on the project.")
      .setResponseExample(Resources.getResource(getClass(), "quality_gate-example.svg"));
    action.createParam(PARAM_COMPONENT)
      .setDescription("Project key")
      .setRequired(true)
      .setExampleValue(KEY_PROJECT_EXAMPLE_001);
    action.createParam(PARAM_TYPE)
      .setDescription("Type of badge.")
      .setRequired(false)
      .setPossibleValues(asList("BADGE", "CARD"));
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      ComponentDto project = getProject(dbSession, request.mandatoryParam(PARAM_COMPONENT));
      userSession.checkComponentPermission(USER, project);
      Optional<String> qualityGate = getQualityGate(dbSession, project);
      response.stream().setMediaType("image/svg+xml");
      copy(Resources.getResource(getClass(), getSvgFile(qualityGate)).openStream(), response.stream().output());
    }
  }

  private ComponentDto getProject(DbSession dbSession, String projectKey) {
    return componentFinder.getByKey(dbSession, projectKey);
  }

  private Optional<String> getQualityGate(DbSession dbSession, ComponentDto project) {
    Optional<LiveMeasureDto> measure = dbClient.liveMeasureDao().selectMeasure(dbSession, project.uuid(), ALERT_STATUS_KEY);
    return measure.map(LiveMeasureDto::getTextValue);
  }

  private static String getSvgFile(Optional<String> qualityGate) {
    if (!qualityGate.isPresent()) {
      return "quality_gate-not_found.svg";
    }
    String value = qualityGate.get();
    switch (value) {
      case "OK":
        return "quality_gate-passing.svg";
      case "WARN":
        return "quality_gate-warning.svg";
      case "ERROR":
        return "quality_gate-failing.svg";
      default:
        throw new IllegalStateException(format("Invalid quality gate '%s'", value));
    }
  }

}
