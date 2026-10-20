# Codebase discovery

This project uses codebase-memory-mcp. Always prefer graph tools for code discovery:
`search_graph`, `trace_path`, `get_code_snippet`, `query_graph`, and `get_architecture`.
Run `index_repository` first when the project is not indexed.
Use text/file search for literals, error messages, configuration and non-code files,
or when graph results are insufficient.

# Delivery preference

The user requests deployment to the Windows server using the existing BAT-based
Windows service workflow. After the relevant checks pass, build the B and C
frontends and backend with `deploy/build-release.ps1`, then prepare a versioned
release ZIP. Do not deploy with Docker unless the user explicitly changes this
preference. The release is installed on the server by running
`deploy-windows-service.bat deploy` as Administrator from its extracted version
directory. Preserve the existing database, media directory, and runtime config;
back up the database before applying new migrations. Never reset volumes or
replace existing data as part of deployment. Database credentials are in the
ignored `.env` and `.env.docker`; do not print or commit them. See
`deploy/README.md` for the Windows release and deployment workflow.

Reuse the existing database and media directory. Back up the database before applying
new migrations. Never reset volumes or replace existing data as part of deployment.
Database credentials are in the ignored `.env` and `.env.docker`; do not print or commit them.
See `docs/engineering/content-publishing.md` for the current deployment layout.
