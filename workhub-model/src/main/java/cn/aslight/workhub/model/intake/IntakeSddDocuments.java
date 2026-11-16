package cn.aslight.workhub.model.intake;

/**
 * SDD 模式评估产出的三件套草稿内容。
 */
public record IntakeSddDocuments(String sddProposal,
                                 String sddDesign,
                                 String sddTasks) {

    public boolean hasAny() {
        return sddProposal != null || sddDesign != null || sddTasks != null;
    }
}
