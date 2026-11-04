package cn.aslight.workhub.model.autonomy;

import cn.aslight.workhub.model.agent.AgentPage;
import java.util.List;

public record AutonomyCaseDetailResponse(AutonomyCaseEntity caseInfo, AgentPage<AutonomySignalEntity> signals,
                                          AgentPage<AutonomyProposalEntity> proposals,
                                          List<AutonomyCaseRunEntity> relatedRuns, long relatedRunTotal, AutonomyBugContext bugContext) { }
