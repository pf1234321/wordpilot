/*
 * Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0.
 */

/**
 * 模型服务：LLM（阿里云百炼 DashScope API：Qwen）/ Embedding（BGE bge-m3，本地 ONNX Runtime）.
 *
 * <p>两条调用链独立、可切换；API key 与本地模型路径一律 {@code ${ENV_VAR}}，敏感配置不入代码.
 */
package com.scriptagent.writing.model;
