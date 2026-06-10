package com.maul.onchain3d.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maul.onchain3d.copilot.dto.SceneCommand;
import com.maul.onchain3d.copilot.dto.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommandValidatorTest {

    private CommandValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CommandValidator(new ObjectMapper());
    }

    @Test
    void allValidCommandsPassThrough() {
        List<SceneCommand> commands = List.of(
                new SceneCommand.FlyTo("addr1", 500),
                new SceneCommand.Focus("addr2", 1),
                new SceneCommand.Highlight(List.of("addr1", "addr2"), "pulse")
        );
        Set<String> allowed = Set.of("addr1", "addr2");

        ValidationResult result = validator.validate(commands, allowed);

        assertThat(result.commands()).hasSize(3);
        assertThat(result.grounded()).isTrue();
    }

    @Test
    void commandWithUnknownNodeIdIsDroppedAndGroundedFalse() {
        List<SceneCommand> commands = List.of(
                new SceneCommand.FlyTo("addr1", 500),
                new SceneCommand.FlyTo("unknown_addr", 500)
        );
        Set<String> allowed = Set.of("addr1");

        ValidationResult result = validator.validate(commands, allowed);

        assertThat(result.commands()).hasSize(1);
        assertThat(result.grounded()).isFalse();
    }

    @Test
    void commandsAreCappedAtTwelve() {
        List<SceneCommand> commands = new ArrayList<>();
        Set<String> allowed = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String addr = "addr" + i;
            commands.add(new SceneCommand.FlyTo(addr, 500));
            allowed.add(addr);
        }

        ValidationResult result = validator.validate(commands, allowed);

        assertThat(result.commands()).hasSize(12);
        // grounded is false because raw list size (20) != filtered size (12)
        assertThat(result.grounded()).isFalse();
    }

    @Test
    void filterAndResetViewAlwaysPass() {
        List<SceneCommand> commands = List.of(
                new SceneCommand.Filter("1h", 0.01, "SOL"),
                new SceneCommand.ResetView()
        );

        ValidationResult result = validator.validate(commands, Set.of());

        assertThat(result.commands()).hasSize(2);
        assertThat(result.grounded()).isTrue();
    }

    @Test
    void emptyInputReturnsGroundedFalseAndEmptyList() {
        ValidationResult result = validator.validate(List.of(), Set.of("addr1"));

        assertThat(result.commands()).isEmpty();
        assertThat(result.grounded()).isFalse();
    }

    @Test
    void highlightDroppedWhenAnyNodeIdUnknown() {
        List<SceneCommand> commands = List.of(
                new SceneCommand.Highlight(List.of("addr1", "bad_addr"), "glow")
        );

        ValidationResult result = validator.validate(commands, Set.of("addr1"));

        assertThat(result.commands()).isEmpty();
        assertThat(result.grounded()).isFalse();
    }

    @Test
    void mixedBagFiltersCorrectly() {
        List<SceneCommand> commands = List.of(
                new SceneCommand.ResetView(),
                new SceneCommand.Focus("addr1", 2),
                new SceneCommand.Focus("bad_addr", 2),
                new SceneCommand.Filter("1h", null, null)
        );
        Set<String> allowed = Set.of("addr1");

        ValidationResult result = validator.validate(commands, allowed);

        assertThat(result.commands()).hasSize(3);
        assertThat(result.grounded()).isFalse();
    }
}
