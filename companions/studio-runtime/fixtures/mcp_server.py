"""Local test fixture only. Never registered in the product connector list."""
import sys
from pathlib import Path
from mcp.server.mcpserver import MCPServer
server=MCPServer('meshlit-fixture')
@server.tool()
def echo(text: str) -> str:
    """Echo text and record that this fixture tool really executed."""
    Path(sys.argv[1]).write_text('executed')
    return 'MCP fixture: '+text
server.run(transport='stdio')
