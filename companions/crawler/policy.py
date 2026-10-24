"""Crawler scope validation. Network isolation is still required in deployment."""
import asyncio
import ipaddress
import socket
from urllib.parse import urlsplit, urlunsplit

USER_AGENT = "MeshlitCrawler/1.0 (+https://github.com/sabbirimon/meshlit)"


class PolicyError(ValueError):
    pass


class CrawlPolicy:
    def __init__(self, domains: str):
        self.domains = frozenset(
            domain.strip().lower().encode("idna").decode("ascii")
            for domain in domains.split(",") if domain.strip()
        )
        if not self.domains or any(
            "/" in domain or ":" in domain or "*" in domain or domain.endswith(".")
            for domain in self.domains
        ):
            raise ValueError("MESHLIT_CRAWL_DOMAINS must list exact hostnames, without wildcards")

    def normalize(self, url: str) -> str:
        try:
            parsed = urlsplit(url)
            host = (parsed.hostname or "").encode("idna").decode("ascii").lower()
            if (parsed.scheme != "https" or parsed.username is not None
                    or parsed.password is not None or parsed.port not in (None, 443)
                    or host not in self.domains or len(url) > 4096
                    or any(ord(char) < 33 or char == "\\" for char in url)):
                raise PolicyError("Only HTTPS URLs on operator-approved exact hostnames are allowed")
            return urlunsplit(("https", host, parsed.path or "/", parsed.query, ""))
        except (ValueError, UnicodeError) as exc:
            raise PolicyError("Invalid or unapproved URL") from exc

    async def validate(self, url: str) -> str:
        normalized = self.normalize(url)
        host = urlsplit(normalized).hostname
        try:
            addresses = await asyncio.get_running_loop().getaddrinfo(
                host, 443, type=socket.SOCK_STREAM
            )
        except OSError as exc:
            raise PolicyError("Target DNS resolution failed") from exc
        if not addresses or any(
            not ipaddress.ip_address(address[4][0]).is_global
            or getattr(ipaddress.ip_address(address[4][0]), "ipv4_mapped", None) is not None
            for address in addresses
        ):
            raise PolicyError("Targets resolving to non-public addresses are prohibited")
        return normalized


class StrictRobots:
    """Per-crawl robots rules; fail closed except for a missing robots file."""
    def __init__(self, policy: CrawlPolicy):
        self.policy = policy
        self.rules = {}
        self.last_fetch = {}

    async def can_fetch(self, url: str, user_agent: str = USER_AGENT) -> bool:
        from urllib.robotparser import RobotFileParser
        import httpx

        normalized = await self.policy.validate(url)
        host = urlsplit(normalized).hostname
        if host not in self.rules:
            robots_url = await self.policy.validate(f"https://{host}/robots.txt")
            try:
                async with httpx.AsyncClient(
                    timeout=5, follow_redirects=False, trust_env=False
                ) as client:
                    async with client.stream(
                        "GET", robots_url, headers={"User-Agent": USER_AGENT}
                    ) as response:
                        if response.status_code == 404:
                            lines = []
                        elif response.status_code == 200:
                            body = bytearray()
                            async for chunk in response.aiter_bytes():
                                body.extend(chunk)
                                if len(body) > 512000:
                                    return False
                            lines = body.decode("utf-8", errors="replace").splitlines()
                        else:
                            return False
            except httpx.HTTPError:
                return False
            rules = RobotFileParser(robots_url)
            rules.parse(lines)
            self.rules[host] = rules
        return self.rules[host].can_fetch(user_agent, normalized)

    async def wait_for_delay(self, url: str):
        import time
        host = urlsplit(url).hostname
        rules = self.rules.get(host)
        delay = max(2, (rules.crawl_delay(USER_AGENT) if rules else None) or 0)
        rate = rules.request_rate(USER_AGENT) if rules else None
        if rate and rate.requests > 0:
            delay = max(delay, rate.seconds / rate.requests)
        await asyncio.sleep(max(0, delay - (time.monotonic() - self.last_fetch.get(host, 0))))
        self.last_fetch[host] = time.monotonic()
