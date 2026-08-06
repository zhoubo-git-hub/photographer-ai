package com.photogai.modules.studio.dto;

import com.photogai.modules.studio.entity.Studio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作室视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudioDTO {

    private Long id;
    private String name;
    private String planType;
    private Long ownerUserId;

    public static StudioDTO from(Studio studio) {
        if (studio == null) {
            return null;
        }
        return StudioDTO.builder()
                .id(studio.getId())
                .name(studio.getName())
                .planType(studio.getPlanType())
                .ownerUserId(studio.getOwnerUserId())
                .build();
    }
}
