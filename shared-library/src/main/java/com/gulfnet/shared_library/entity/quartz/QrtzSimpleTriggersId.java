package com.gulfnet.shared_library.entity.quartz;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class QrtzSimpleTriggersId implements Serializable {
    
    private String schedName;
    private String triggerName;
    private String triggerGroup;
} 