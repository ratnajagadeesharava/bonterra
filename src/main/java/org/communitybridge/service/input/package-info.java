/**
 * Service inputs, one record per operation. Fields are deliberately raw (String, Integer) so the
 * service can report every bad value as VALIDATION_ERROR naming the field, instead of failing
 * while the input is being converted. None of them carries identity, role or nonprofit:
 * those come only from {@link org.communitybridge.domain.CallerContext}.
 * Unknown-field rejection happens where JSON is parsed (the MCP layer, step 6).
 */
package org.communitybridge.service.input;
