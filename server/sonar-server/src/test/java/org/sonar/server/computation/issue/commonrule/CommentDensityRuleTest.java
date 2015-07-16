/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.computation.issue.commonrule;

import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.server.computation.batch.TreeRootHolderRule;
import org.sonar.server.computation.component.Component;
import org.sonar.server.computation.component.DumbComponent;
import org.sonar.server.computation.component.FileAttributes;
import org.sonar.server.computation.measure.Measure;
import org.sonar.server.computation.measure.MeasureRepositoryRule;
import org.sonar.server.computation.metric.MetricRepositoryRule;
import org.sonar.server.computation.qualityprofile.ActiveRule;
import org.sonar.server.computation.qualityprofile.ActiveRulesHolderRule;
import org.sonar.server.rule.CommonRuleKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.server.computation.component.DumbComponent.DUMB_PROJECT;

public class CommentDensityRuleTest {

  static RuleKey RULE_KEY = RuleKey.of(CommonRuleKeys.commonRepositoryForLang("java"), CommonRuleKeys.INSUFFICIENT_COMMENT_DENSITY);

  static DumbComponent FILE = DumbComponent.builder(Component.Type.FILE, 1)
    .setFileAttributes(new FileAttributes(false, "java"))
    .build();

  @Rule
  public ActiveRulesHolderRule activeRuleHolder = new ActiveRulesHolderRule();

  @Rule
  public MetricRepositoryRule metricRepository = new MetricRepositoryRule()
    .add(CoreMetrics.COMMENT_LINES_DENSITY)
    .add(CoreMetrics.COMMENT_LINES)
    .add(CoreMetrics.NCLOC);

  @Rule
  public TreeRootHolderRule treeRootHolder = new TreeRootHolderRule().setRoot(DUMB_PROJECT);

  @Rule
  public MeasureRepositoryRule measureRepository = MeasureRepositoryRule.create(treeRootHolder, metricRepository);

  CommentDensityRule underTest = new CommentDensityRule(activeRuleHolder, measureRepository, metricRepository);

  @Test
  public void no_issues_if_enough_comments() {
    activeRuleHolder.put(new ActiveRule(RULE_KEY, Severity.CRITICAL));
    measureRepository.addRawMeasure(FILE.getRef(), CoreMetrics.COMMENT_LINES_DENSITY_KEY, Measure.newMeasureBuilder().create(90.0));

    DefaultIssue issue = underTest.processFile(FILE, "java");

    assertThat(issue).isNull();
  }

  @Test
  public void issue_if_not_enough_comments() {
    activeRuleHolder.put(new ActiveRule(RULE_KEY, Severity.CRITICAL));
    measureRepository.addRawMeasure(FILE.getRef(), CoreMetrics.COMMENT_LINES_DENSITY_KEY, Measure.newMeasureBuilder().create(10.0));
    measureRepository.addRawMeasure(FILE.getRef(), CoreMetrics.COMMENT_LINES_KEY, Measure.newMeasureBuilder().create(40));
    measureRepository.addRawMeasure(FILE.getRef(), CoreMetrics.NCLOC_KEY, Measure.newMeasureBuilder().create(360));

    DefaultIssue issue = underTest.processFile(FILE, "java");

    assertThat(issue.ruleKey()).isEqualTo(RULE_KEY);
    assertThat(issue.severity()).isEqualTo(Severity.CRITICAL);
    // min_comments = (min_percent * ncloc) / (1 - min_percent)
    // -> threshold of 25% for 360 ncloc is 120 comment lines. 40 are already written.
    assertThat(issue.effortToFix()).isEqualTo(120.0 - 40.0);
    assertThat(issue.message()).isEqualTo("80 more comment lines need to be written to reach the minimum threshold of 25.0% comment density.");
  }

  @Test
  public void issue_if_not_enough_comments__test_ceil() {
    activeRuleHolder.put(new ActiveRule(RULE_KEY, Severity.CRITICAL));
    measureRepository.addRawMeasure(FILE.getRef(), CoreMetrics.COMMENT_LINES_DENSITY_KEY, Measure.newMeasureBuilder().create(0.0));
    measureRepository.addRawMeasure(FILE.getRef(), CoreMetrics.COMMENT_LINES_KEY, Measure.newMeasureBuilder().create(0));
    measureRepository.addRawMeasure(FILE.getRef(), CoreMetrics.NCLOC_KEY, Measure.newMeasureBuilder().create(1));

    DefaultIssue issue = underTest.processFile(FILE, "java");

    assertThat(issue.ruleKey()).isEqualTo(RULE_KEY);
    assertThat(issue.severity()).isEqualTo(Severity.CRITICAL);
    // 1 ncloc requires 1 comment line to reach 25% of comment density
    assertThat(issue.effortToFix()).isEqualTo(1.0);
    assertThat(issue.message()).isEqualTo("1 more comment lines need to be written to reach the minimum threshold of 25.0% comment density.");
  }

  // /**
  // * SQALE-110
  // */
  // @Test
  // public void shouldFailIfMinimumCommentDensitySetTo100() {
  // check.setMinimumCommentDensity(100);
  //
  // thrown.expect(IllegalArgumentException.class);
  // thrown.expectMessage("100.0 is not a valid value for minimum required comment density for rule 'CommentDensityCheck' (must be >= 0 and < 100).");
  //
  // check.checkResource(resource, context, null, perspectives);
  //
  // verify(perspectives, times(0)).as(Issuable.class, resource);
  // }
  //
  // /**
  // * SQALE-110
  // */
  // @Test
  // public void shouldFailIfMinimumCommentDensitySetToNegative() {
  // check.setMinimumCommentDensity(-5);
  //
  // thrown.expect(IllegalArgumentException.class);
  // thrown.expectMessage("-5.0 is not a valid value for minimum required comment density for rule 'CommentDensityCheck' (must be >= 0 and < 100).");
  //
  // check.checkResource(resource, context, null, mock(ResourcePerspectives.class));
  //
  // verify(perspectives, times(0)).as(Issuable.class, resource);
  // }
}