/**
 * What services return; the MCP layer sends these as structuredContent unchanged.
 * Every amount appears twice: *Cents (long, for programs) and a "300.50" string (for people),
 * and every money object carries currency "USD". Times are UTC ISO-8601 strings.
 */
package org.communitybridge.service.result;
