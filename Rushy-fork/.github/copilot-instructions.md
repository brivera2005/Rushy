## graphify

If a task mentions Graphify or requires building, updating, or querying the knowledge graph, load the global `graphify` skill first if it is not already loaded in the current session. Treat the loaded Graphify skill as the source of truth for invocation and workflow details rather than inferring commands from memory.

Before answering architecture or codebase questions, read `graphify-out/GRAPH_REPORT.md` if it exists.
If `graphify-out/wiki/index.md` exists, navigate it for deep questions.
Type `/graphify` in Copilot Chat to build or update the knowledge graph.
