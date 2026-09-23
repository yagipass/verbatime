# Using an AI agent

[`skills/vbtm/`](https://github.com/yagipass/verbatime/blob/main/skills/vbtm/SKILL.md) is an agent
skill that investigates a recording with [vbtm](./reading-with-vbtm.md). Add it to your agent, and
ask why a request was slow.

The agent follows the same steps a person would:

1. `vbtm sessions rec.vbtm --sort dur` to pick a slow session.
2. `vbtm hot rec.vbtm 7` to find the methods with the most self time.
3. `vbtm throws rec.vbtm 7` to find exceptions and where they are caught.
4. `vbtm tree rec.vbtm --at 7.57` to follow one call down to the method that holds the time.

Every output is short and ends with the command that shows more, so a whole recording never fills
the agent's context.
