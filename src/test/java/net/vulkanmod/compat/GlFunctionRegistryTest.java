package net.vulkanmod.compat;

import net.vulkanmod.compat.opengl.GlFunctionRegistry;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlFunctionRegistryTest {

    /** GL.createCapabilities() aborts without these. */
    @Test
    void capabilityBootstrapFunctionsAreRegistered() {
        for (String name : List.of("glGetError", "glGetString", "glGetIntegerv", "glGetStringi",
                "glGetFloatv", "glGetBooleanv", "glGetDoublev", "glGetInteger64v")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must be registered for capability creation");
        }
    }

    /**
     * The core-profile sets through GL 3.2 must resolve completely, or the
     * computed GLCapabilities version drops and mods take render paths the
     * emulation does not cover. GL 3.3 functions stay callable too - except
     * instancing, see below.
     */
    @Test
    void coreSurfaceThroughGl33IsRegistered() {
        for (String name : List.of(
                // GL11 core
                "glBindTexture", "glTexImage2D", "glTexSubImage2D", "glDeleteTextures", "glGenTextures",
                "glDrawArrays", "glDrawElements", "glViewport", "glEnable", "glDisable", "glBlendFunc",
                "glDepthFunc", "glPixelStorei", "glReadPixels", "glIsEnabled",
                // GL12-GL15
                "glDrawRangeElements", "glActiveTexture", "glBlendFuncSeparate", "glGenBuffers",
                "glBindBuffer", "glBufferData", "glBufferSubData", "glMapBuffer", "glUnmapBuffer",
                "glGenQueries", "glBeginQuery",
                // GL20-GL21
                "glCreateProgram", "glCreateShader", "glShaderSource", "glCompileShader", "glLinkProgram",
                "glUseProgram", "glGetUniformLocation", "glUniform1i", "glUniformMatrix4fv",
                "glVertexAttribPointer", "glEnableVertexAttribArray", "glDrawBuffers", "glUniformMatrix2x3fv",
                // GL30-GL33
                "glGenFramebuffers", "glBindFramebuffer", "glFramebufferTexture2D", "glCheckFramebufferStatus",
                "glBlitFramebuffer", "glGenerateMipmap", "glGenVertexArrays", "glBindVertexArray",
                "glMapBufferRange", "glBindBufferBase", "glCopyBufferSubData", "glFenceSync",
                "glClientWaitSync", "glFramebufferTexture", "glGenSamplers", "glBindSampler",
                "glQueryCounter", "glVertexAttribP1ui",
                // Indexed getters LWJGL requests while computing version flags
                "glGetIntegeri_v", "glGetBooleani_v", "glGetInteger64i_v")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must be registered to sustain core GL");
        }
    }

    /**
     * Per-instance vertex data has no emulation, so GL 3.3 must not be
     * advertised: capability-probing renderers (Flywheel-style GL backends,
     * generically) gate instancing on the computed OpenGL33 flag and would
     * otherwise issue instanced draws that no-op into invisible geometry.
     * The version cap, not function absence, is the gate - 3.3 entry points
     * stay registered so unconditional callers (e.g. divisor 0 from
     * non-instanced vertex setup) are absorbed without crashing.
     */
    @Test
    void instancingIsNotAdvertisedWhileCallsAreAbsorbed() {
        assertTrue(GlFunctionRegistry.REPORTED_GL_VERSION.startsWith("3.2"),
                "reported GL version must stay below 3.3 while instancing has no execution path");

        for (String name : List.of("glVertexAttribDivisor", "glDrawArraysInstanced", "glDrawElementsInstanced",
                "glDrawElementsInstancedBaseVertex")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must stay registered to absorb stray calls");
        }
    }

    /** Veil and other DSA users must land in the emulation, never on a driver. */
    @Test
    void directStateAccessSurfaceIsRegistered() {
        for (String name : List.of("glCreateTextures", "glCreateFramebuffers", "glCreateBuffers",
                "glTextureStorage2D", "glTextureSubImage2D", "glTextureParameteri", "glBindTextureUnit",
                "glNamedFramebufferTexture", "glCheckNamedFramebufferStatus", "glNamedFramebufferDrawBuffers",
                "glNamedBufferData", "glMapNamedBuffer", "glGenerateTextureMipmap")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must be registered for DSA callers");
        }
    }

    @Test
    void modernDsaReadbackAndObjectCreationEntrypointsResolveThroughProvider() {
        for (String name : List.of("glCreateProgramPipelines", "glCreateTransformFeedbacks",
                "glGetNamedBufferSubData", "glGetNamedBufferParameteriv", "glGetNamedBufferParameteri",
                "glGetNamedBufferParameteri64v", "glGetNamedBufferParameteri64",
                "glClearNamedBufferData", "glClearNamedBufferSubData",
                "glGetTextureImage", "glGetTextureParameteriv", "glGetTextureParameteri",
                "glGetTextureLevelParameteriv", "glGetTextureLevelParameteri", "glGetGraphicsResetStatus")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void registeredFunctionsExposeUniversalContractFamilies() throws Exception {
        Method allFunctions = GlFunctionRegistry.class.getDeclaredMethod("allFunctionsForTesting");
        allFunctions.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> functions = (Map<String, ?>) allFunctions.invoke(null);

        Method contractFamily = GlFunctionRegistry.class.getDeclaredMethod("contractFamilyForTesting", String.class);
        contractFamily.setAccessible(true);

        for (String name : functions.keySet()) {
            String family = (String) contractFamily.invoke(null, name);
            assertNotNull(family, "Missing GL contract family for " + name);
            assertFalse(family.isBlank(), "Blank GL contract family for " + name);
        }
    }

    @Test
    void modernControlPlaneEntrypointsResolveThroughProvider() {
        for (String name : List.of("glGenTransformFeedbacks", "glDeleteTransformFeedbacks",
                "glBindTransformFeedback", "glIsTransformFeedback", "glPauseTransformFeedback",
                "glResumeTransformFeedback", "glDrawTransformFeedback", "glDrawTransformFeedbackStream",
                "glBeginQueryIndexed", "glEndQueryIndexed", "glGetQueryIndexediv", "glGetQueryIndexedi",
                "glGetProgramStageiv", "glGetProgramStagei", "glGetSubroutineIndex",
                "glGetSubroutineUniformLocation", "glUniformSubroutinesuiv", "glGetUniformSubroutineuiv",
                "glGenProgramPipelines", "glDeleteProgramPipelines", "glBindProgramPipeline",
                "glIsProgramPipeline", "glUseProgramStages", "glActiveShaderProgram",
                "glValidateProgramPipeline", "glGetProgramPipelineiv", "glGetProgramPipelinei",
                "glGetProgramPipelineInfoLog", "glCreateShaderProgramv", "glProgramBinary",
                "glGetProgramBinary", "glShaderBinary", "glViewportArrayv", "glViewportIndexedf",
                "glViewportIndexedfv", "glScissorArrayv", "glScissorIndexed", "glScissorIndexedv",
                "glDepthRangeArrayv", "glDepthRangeIndexed", "glGetFloati_v", "glGetFloati",
                "glGetDoublei_v", "glGetDoublei")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void gl33PackedAndScalarQueryEntrypointsResolveThroughProvider() {
        for (String name : List.of("glColorP3ui", "glColorP3uiv", "glColorP4ui", "glColorP4uiv",
                "glGetQueryObjecti64", "glGetQueryObjectui64", "glGetSamplerParameterIi",
                "glGetSamplerParameterIui", "glGetSamplerParameterf", "glGetSamplerParameteri",
                "glMultiTexCoordP1ui", "glMultiTexCoordP1uiv", "glMultiTexCoordP2ui",
                "glMultiTexCoordP2uiv", "glMultiTexCoordP3ui", "glMultiTexCoordP3uiv",
                "glMultiTexCoordP4ui", "glMultiTexCoordP4uiv", "glNormalP3ui", "glNormalP3uiv",
                "glSecondaryColorP3ui", "glSecondaryColorP3uiv", "glTexCoordP1ui",
                "glTexCoordP1uiv", "glTexCoordP2ui", "glTexCoordP2uiv", "glTexCoordP3ui",
                "glTexCoordP3uiv", "glTexCoordP4ui", "glTexCoordP4uiv", "glVertexP2ui",
                "glVertexP2uiv", "glVertexP3ui", "glVertexP3uiv", "glVertexP4ui", "glVertexP4uiv")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void gl42ToGl44UtilityEntrypointsResolveThroughProvider() {
        for (String name : List.of("glDrawElementsInstancedBaseInstance",
                "glDrawElementsInstancedBaseVertexBaseInstance", "glDrawTransformFeedbackInstanced",
                "glDrawTransformFeedbackStreamInstanced", "glGetActiveAtomicCounterBufferi",
                "glGetActiveAtomicCounterBufferiv", "glGetInternalformati", "glGetInternalformativ",
                "glBindVertexBuffer", "glClearBufferData", "glClearBufferSubData", "glDispatchComputeIndirect",
                "glGetDebugMessageLog", "glGetFramebufferParameteri", "glGetInternalformati64",
                "glGetInternalformati64v", "glGetObjectPtrLabel", "glGetProgramInterfacei",
                "glGetProgramInterfaceiv", "glGetProgramResourceIndex", "glGetProgramResourceLocation",
                "glGetProgramResourceLocationIndex", "glGetProgramResourceName", "glGetProgramResourceiv",
                "glInvalidateBufferData", "glInvalidateBufferSubData", "glInvalidateSubFramebuffer",
                "glInvalidateTexImage", "glInvalidateTexSubImage", "glMultiDrawArraysIndirect",
                "glObjectPtrLabel", "glTexBufferRange", "glTexStorage3DMultisample", "glTextureView",
                "glVertexAttribBinding", "glVertexAttribFormat", "glVertexAttribIFormat",
                "glVertexAttribLFormat", "glVertexBindingDivisor", "glBindBuffersRange",
                "glBindVertexBuffers", "glClearTexSubImage")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void gl40AndGl41NumericEntrypointsResolveThroughProvider() {
        for (String name : List.of("glGetUniformd", "glPatchParameterfv",
                "glUniform1d", "glUniform1dv", "glUniform2d", "glUniform2dv",
                "glUniform3d", "glUniform3dv", "glUniform4d", "glUniform4dv",
                "glUniformMatrix2dv", "glUniformMatrix2x3dv", "glUniformMatrix2x4dv",
                "glUniformMatrix3dv", "glUniformMatrix3x2dv", "glUniformMatrix3x4dv",
                "glUniformMatrix4dv", "glUniformMatrix4x2dv", "glUniformMatrix4x3dv",
                "glGetShaderPrecisionFormat", "glGetVertexAttribLdv",
                "glProgramUniform1d", "glProgramUniform2d", "glProgramUniform2ui",
                "glProgramUniform3d", "glProgramUniform3ui", "glProgramUniform4d",
                "glProgramUniform4ui", "glProgramUniformMatrix2dv", "glProgramUniformMatrix2x3dv",
                "glProgramUniformMatrix2x4dv", "glProgramUniformMatrix3dv",
                "glProgramUniformMatrix3x2dv", "glProgramUniformMatrix3x4dv",
                "glProgramUniformMatrix4dv", "glProgramUniformMatrix4x2dv",
                "glProgramUniformMatrix4x3dv", "glVertexAttribL1d", "glVertexAttribL1dv",
                "glVertexAttribL2d", "glVertexAttribL2dv", "glVertexAttribL3d",
                "glVertexAttribL3dv", "glVertexAttribL4d", "glVertexAttribL4dv")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void gl45RemainingEntrypointsResolveThroughProvider() {
        for (String name : List.of("glCompressedTextureSubImage1D", "glCompressedTextureSubImage2D",
                "glCompressedTextureSubImage3D", "glCopyTextureSubImage1D", "glCopyTextureSubImage2D",
                "glCopyTextureSubImage3D", "glDisableVertexArrayAttrib", "glEnableVertexArrayAttrib",
                "glGetNamedFramebufferAttachmentParameteri", "glGetNamedFramebufferAttachmentParameteriv",
                "glGetNamedFramebufferParameteri", "glGetNamedFramebufferParameteriv",
                "glGetNamedRenderbufferParameteri", "glGetNamedRenderbufferParameteriv",
                "glGetQueryBufferObjecti64v", "glGetQueryBufferObjectiv", "glGetQueryBufferObjectui64v",
                "glGetQueryBufferObjectuiv", "glGetTextureLevelParameterf", "glGetTextureLevelParameterfv",
                "glGetTextureParameterf", "glGetTextureParameterfv", "glGetTransformFeedbacki",
                "glGetTransformFeedbacki64", "glGetTransformFeedbacki64_v", "glGetTransformFeedbacki_v",
                "glGetTransformFeedbackiv", "glGetVertexArrayIndexed64i", "glGetVertexArrayIndexed64iv",
                "glGetVertexArrayIndexedi", "glGetVertexArrayIndexediv", "glGetVertexArrayi",
                "glGetVertexArrayiv", "glGetnColorTable", "glGetnCompressedTexImage",
                "glGetnConvolutionFilter", "glGetnHistogram", "glGetnMapd", "glGetnMapdv",
                "glGetnMapf", "glGetnMapfv", "glGetnMapi", "glGetnMapiv", "glGetnMinmax",
                "glGetnPixelMapfv", "glGetnPixelMapuiv", "glGetnPixelMapusv", "glGetnPolygonStipple",
                "glGetnSeparableFilter", "glGetnTexImage", "glGetnUniformd", "glGetnUniformdv",
                "glGetnUniformf", "glGetnUniformfv", "glGetnUniformi", "glGetnUniformiv",
                "glGetnUniformui", "glGetnUniformuiv", "glInvalidateNamedFramebufferData",
                "glInvalidateNamedFramebufferSubData", "glMemoryBarrierByRegion", "glNamedFramebufferParameteri",
                "glReadnPixels", "glTextureBarrier", "glTextureBuffer", "glTextureBufferRange",
                "glTextureParameterIi", "glTextureParameterIiv", "glTextureParameterIui",
                "glTextureParameterIuiv", "glTextureParameterfv", "glTextureParameteriv",
                "glTextureStorage1D", "glTextureStorage2DMultisample", "glTextureStorage3D",
                "glTextureStorage3DMultisample", "glTextureSubImage1D", "glTextureSubImage3D",
                "glTransformFeedbackBufferBase", "glTransformFeedbackBufferRange",
                "glVertexArrayAttribBinding", "glVertexArrayAttribFormat", "glVertexArrayAttribIFormat",
                "glVertexArrayAttribLFormat", "glVertexArrayBindingDivisor", "glVertexArrayElementBuffer",
                "glVertexArrayVertexBuffer", "glVertexArrayVertexBuffers")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void legacyArbAndExtEntrypointsResolveThroughProvider() {
        for (String name : List.of("glDebugMessageCallbackARB", "glDebugMessageControlARB",
                "glDebugMessageInsertARB", "glGetDebugMessageLogARB",
                "glActiveTextureARB", "glClientActiveTextureARB", "glMultiTexCoord1dARB",
                "glMultiTexCoord1dvARB", "glMultiTexCoord1fARB", "glMultiTexCoord1fvARB",
                "glMultiTexCoord1iARB", "glMultiTexCoord1ivARB", "glMultiTexCoord1sARB",
                "glMultiTexCoord1svARB", "glMultiTexCoord2dARB", "glMultiTexCoord2dvARB",
                "glMultiTexCoord2fARB", "glMultiTexCoord2fvARB", "glMultiTexCoord2iARB",
                "glMultiTexCoord2ivARB", "glMultiTexCoord2sARB", "glMultiTexCoord2svARB",
                "glMultiTexCoord3dARB", "glMultiTexCoord3dvARB", "glMultiTexCoord3fARB",
                "glMultiTexCoord3fvARB", "glMultiTexCoord3iARB", "glMultiTexCoord3ivARB",
                "glMultiTexCoord3sARB", "glMultiTexCoord3svARB", "glMultiTexCoord4dARB",
                "glMultiTexCoord4dvARB", "glMultiTexCoord4fARB", "glMultiTexCoord4fvARB",
                "glMultiTexCoord4iARB", "glMultiTexCoord4ivARB", "glMultiTexCoord4sARB",
                "glMultiTexCoord4svARB", "glBindBufferARB", "glBufferDataARB", "glBufferSubDataARB",
                "glDeleteBuffersARB", "glGenBuffersARB", "glGetBufferParameteriARB",
                "glGetBufferParameterivARB", "glGetBufferPointerARB", "glGetBufferPointervARB",
                "glGetBufferSubDataARB", "glIsBufferARB", "glMapBufferARB", "glUnmapBufferARB",
                "glBindFramebufferEXT", "glBindRenderbufferEXT", "glCheckFramebufferStatusEXT",
                "glDeleteFramebuffersEXT", "glDeleteRenderbuffersEXT", "glFramebufferRenderbufferEXT",
                "glFramebufferTexture1DEXT", "glFramebufferTexture2DEXT", "glFramebufferTexture3DEXT",
                "glGenFramebuffersEXT", "glGenRenderbuffersEXT", "glGenerateMipmapEXT",
                "glGetFramebufferAttachmentParameteriEXT", "glGetFramebufferAttachmentParameterivEXT",
                "glGetRenderbufferParameteriEXT", "glGetRenderbufferParameterivEXT",
                "glIsFramebufferEXT", "glIsRenderbufferEXT", "glRenderbufferStorageEXT",
                "glGetFramebufferAttachmentParameteri", "glGetRenderbufferParameteri",
                "glTextureStorage1DEXT", "glTextureStorage2DEXT", "glTextureStorage3DEXT",
                "glTextureStorage2DMultisampleEXT", "glTextureStorage3DMultisampleEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void arbShaderObjectsEntrypointsResolveThroughProvider() {
        for (String name : List.of("glAttachObjectARB", "glCompileShaderARB", "glCreateProgramObjectARB",
                "glCreateShaderObjectARB", "glDeleteObjectARB", "glDetachObjectARB", "glGetActiveUniformARB",
                "glGetAttachedObjectsARB", "glGetHandleARB", "glGetInfoLogARB", "glGetObjectParameterfvARB",
                "glGetObjectParameteriARB", "glGetObjectParameterivARB", "glGetShaderSourceARB",
                "glGetUniformLocationARB", "glGetUniformfARB", "glGetUniformfvARB", "glGetUniformiARB",
                "glGetUniformivARB", "glLinkProgramARB", "glShaderSourceARB", "glUniform1fARB",
                "glUniform1fvARB", "glUniform1iARB", "glUniform1ivARB", "glUniform2fARB",
                "glUniform2fvARB", "glUniform2iARB", "glUniform2ivARB", "glUniform3fARB",
                "glUniform3fvARB", "glUniform3iARB", "glUniform3ivARB", "glUniform4fARB",
                "glUniform4fvARB", "glUniform4iARB", "glUniform4ivARB", "glUniformMatrix2fvARB",
                "glUniformMatrix3fvARB", "glUniformMatrix4fvARB", "glUseProgramObjectARB",
                "glValidateProgramARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void modernArbAndExtAliasEntrypointsResolveThroughProvider() {
        for (String name : List.of("glBeginQueryARB", "glDeleteQueriesARB", "glEndQueryARB",
                "glGenQueriesARB", "glGetQueryObjectiARB", "glGetQueryObjectivARB",
                "glGetQueryObjectuiARB", "glGetQueryObjectuivARB", "glGetQueryiARB",
                "glGetQueryivARB", "glIsQueryARB", "glGetQueryObjecti64EXT",
                "glGetQueryObjecti64vEXT", "glGetQueryObjectui64EXT", "glGetQueryObjectui64vEXT",
                "glGetGraphicsResetStatusARB", "glGetnColorTableARB", "glGetnCompressedTexImageARB",
                "glGetnConvolutionFilterARB", "glGetnHistogramARB", "glGetnMapdARB",
                "glGetnMapdvARB", "glGetnMapfARB", "glGetnMapfvARB", "glGetnMapiARB",
                "glGetnMapivARB", "glGetnMinmaxARB", "glGetnPixelMapfvARB",
                "glGetnPixelMapuivARB", "glGetnPixelMapusvARB", "glGetnPolygonStippleARB",
                "glGetnSeparableFilterARB", "glGetnTexImageARB", "glGetnUniformdARB",
                "glGetnUniformdvARB", "glGetnUniformfARB", "glGetnUniformfvARB",
                "glGetnUniformiARB", "glGetnUniformivARB", "glGetnUniformuiARB",
                "glGetnUniformuivARB", "glReadnPixelsARB", "glBlendEquationSeparateiARB",
                "glBlendEquationiARB", "glBlendFuncSeparateiARB", "glBlendFunciARB",
                "glFramebufferTextureARB", "glFramebufferTextureFaceARB",
                "glFramebufferTextureLayerARB", "glProgramParameteriARB",
                "glFramebufferTextureEXT", "glFramebufferTextureFaceEXT",
                "glFramebufferTextureLayerEXT", "glProgramParameteriEXT",
                "glGetNamedFramebufferParameteriEXT", "glGetNamedFramebufferParameterivEXT",
                "glNamedFramebufferParameteriEXT", "glGetActiveUniformBlocki",
                "glGetActiveUniformsi", "glGetIntegeri")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void arbVertexShaderAndProgramEntrypointsResolveThroughProvider() {
        for (String name : List.of("glBindAttribLocationARB", "glDisableVertexAttribArrayARB",
                "glEnableVertexAttribArrayARB", "glGetActiveAttribARB", "glGetAttribLocationARB",
                "glGetVertexAttribPointerARB", "glGetVertexAttribPointervARB", "glGetVertexAttribdvARB",
                "glGetVertexAttribfvARB", "glGetVertexAttribiARB", "glGetVertexAttribivARB",
                "glVertexAttrib1dARB", "glVertexAttrib1dvARB", "glVertexAttrib1fARB",
                "glVertexAttrib1fvARB", "glVertexAttrib1sARB", "glVertexAttrib1svARB",
                "glVertexAttrib2dARB", "glVertexAttrib2dvARB", "glVertexAttrib2fARB",
                "glVertexAttrib2fvARB", "glVertexAttrib2sARB", "glVertexAttrib2svARB",
                "glVertexAttrib3dARB", "glVertexAttrib3dvARB", "glVertexAttrib3fARB",
                "glVertexAttrib3fvARB", "glVertexAttrib3sARB", "glVertexAttrib3svARB",
                "glVertexAttrib4NbvARB", "glVertexAttrib4NivARB", "glVertexAttrib4NsvARB",
                "glVertexAttrib4NubARB", "glVertexAttrib4NubvARB", "glVertexAttrib4NuivARB",
                "glVertexAttrib4NusvARB", "glVertexAttrib4bvARB", "glVertexAttrib4dARB",
                "glVertexAttrib4dvARB", "glVertexAttrib4fARB", "glVertexAttrib4fvARB",
                "glVertexAttrib4ivARB", "glVertexAttrib4sARB", "glVertexAttrib4svARB",
                "glVertexAttrib4ubvARB", "glVertexAttrib4uivARB", "glVertexAttrib4usvARB",
                "glVertexAttribPointerARB", "glBindProgramARB", "glDeleteProgramsARB",
                "glGenProgramsARB", "glGetProgramEnvParameterdvARB", "glGetProgramEnvParameterfvARB",
                "glGetProgramLocalParameterdvARB", "glGetProgramLocalParameterfvARB",
                "glGetProgramStringARB", "glGetProgramiARB", "glGetProgramivARB",
                "glIsProgramARB", "glProgramEnvParameter4dARB", "glProgramEnvParameter4dvARB",
                "glProgramEnvParameter4fARB", "glProgramEnvParameter4fvARB",
                "glProgramLocalParameter4dARB", "glProgramLocalParameter4dvARB",
                "glProgramLocalParameter4fARB", "glProgramLocalParameter4fvARB",
                "glProgramStringARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void shaderNumericExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glBindFragDataLocationEXT", "glGetFragDataLocationEXT",
                "glGetUniformuiEXT", "glGetUniformuivEXT", "glGetVertexAttribIiEXT",
                "glGetVertexAttribIivEXT", "glGetVertexAttribIuiEXT", "glGetVertexAttribIuivEXT",
                "glUniform1uiEXT", "glUniform1uivEXT", "glUniform2uiEXT", "glUniform2uivEXT",
                "glUniform3uiEXT", "glUniform3uivEXT", "glUniform4uiEXT", "glUniform4uivEXT",
                "glVertexAttribI1iEXT", "glVertexAttribI1ivEXT", "glVertexAttribI1uiEXT",
                "glVertexAttribI1uivEXT", "glVertexAttribI2iEXT", "glVertexAttribI2ivEXT",
                "glVertexAttribI2uiEXT", "glVertexAttribI2uivEXT", "glVertexAttribI3iEXT",
                "glVertexAttribI3ivEXT", "glVertexAttribI3uiEXT", "glVertexAttribI3uivEXT",
                "glVertexAttribI4bvEXT", "glVertexAttribI4iEXT", "glVertexAttribI4ivEXT",
                "glVertexAttribI4svEXT", "glVertexAttribI4ubvEXT", "glVertexAttribI4uiEXT",
                "glVertexAttribI4uivEXT", "glVertexAttribI4usvEXT", "glVertexAttribIPointerEXT",
                "glProgramUniform1dEXT", "glProgramUniform1dvEXT", "glProgramUniform2dEXT",
                "glProgramUniform2dvEXT", "glProgramUniform3dEXT", "glProgramUniform3dvEXT",
                "glProgramUniform4dEXT", "glProgramUniform4dvEXT", "glProgramUniformMatrix2dvEXT",
                "glProgramUniformMatrix2x3dvEXT", "glProgramUniformMatrix2x4dvEXT",
                "glProgramUniformMatrix3dvEXT", "glProgramUniformMatrix3x2dvEXT",
                "glProgramUniformMatrix3x4dvEXT", "glProgramUniformMatrix4dvEXT",
                "glProgramUniformMatrix4x2dvEXT", "glProgramUniformMatrix4x3dvEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void fixedFunctionExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glClearColorIiEXT", "glClearColorIuiEXT",
                "glGetTexParameterIiEXT", "glGetTexParameterIivEXT", "glGetTexParameterIuiEXT",
                "glGetTexParameterIuivEXT", "glTexParameterIiEXT", "glTexParameterIivEXT",
                "glTexParameterIuiEXT", "glTexParameterIuivEXT",
                "glColorMaskIndexedEXT", "glDisableIndexedEXT", "glEnableIndexedEXT",
                "glGetBooleanIndexedEXT", "glGetBooleanIndexedvEXT", "glGetIntegerIndexedEXT",
                "glGetIntegerIndexedvEXT", "glIsEnabledIndexedEXT",
                "glGetVertexAttribLdvEXT", "glVertexArrayVertexAttribLOffsetEXT",
                "glVertexAttribL1dEXT", "glVertexAttribL1dvEXT", "glVertexAttribL2dEXT",
                "glVertexAttribL2dvEXT", "glVertexAttribL3dEXT", "glVertexAttribL3dvEXT",
                "glVertexAttribL4dEXT", "glVertexAttribL4dvEXT", "glVertexAttribLPointerEXT",
                "glSecondaryColor3bEXT", "glSecondaryColor3bvEXT", "glSecondaryColor3dEXT",
                "glSecondaryColor3dvEXT", "glSecondaryColor3fEXT", "glSecondaryColor3fvEXT",
                "glSecondaryColor3iEXT", "glSecondaryColor3ivEXT", "glSecondaryColor3sEXT",
                "glSecondaryColor3svEXT", "glSecondaryColor3ubEXT", "glSecondaryColor3ubvEXT",
                "glSecondaryColor3uiEXT", "glSecondaryColor3uivEXT", "glSecondaryColor3usEXT",
                "glSecondaryColor3usvEXT", "glSecondaryColorPointerEXT",
                "glWindowPos2dARB", "glWindowPos2dvARB", "glWindowPos2fARB", "glWindowPos2fvARB",
                "glWindowPos2iARB", "glWindowPos2ivARB", "glWindowPos2sARB", "glWindowPos2svARB",
                "glWindowPos3dARB", "glWindowPos3dvARB", "glWindowPos3fARB", "glWindowPos3fvARB",
                "glWindowPos3iARB", "glWindowPos3ivARB", "glWindowPos3sARB", "glWindowPos3svARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void legacyUtilityExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glCompressedTexImage1DARB", "glCompressedTexImage2DARB",
                "glCompressedTexImage3DARB", "glCompressedTexSubImage1DARB",
                "glCompressedTexSubImage2DARB", "glCompressedTexSubImage3DARB",
                "glGetCompressedTexImageARB",
                "glBeginTransformFeedbackEXT", "glBindBufferBaseEXT", "glBindBufferOffsetEXT",
                "glBindBufferRangeEXT", "glEndTransformFeedbackEXT",
                "glGetTransformFeedbackVaryingEXT", "glTransformFeedbackVaryingsEXT",
                "glVertexArrayBindVertexBufferEXT", "glVertexArrayVertexAttribBindingEXT",
                "glVertexArrayVertexAttribFormatEXT", "glVertexArrayVertexAttribIFormatEXT",
                "glVertexArrayVertexAttribLFormatEXT", "glVertexArrayVertexBindingDivisorEXT",
                "glCurrentPaletteMatrixARB", "glMatrixIndexPointerARB", "glMatrixIndexubvARB",
                "glMatrixIndexuivARB", "glMatrixIndexusvARB",
                "glCompileShaderIncludeARB", "glDeleteNamedStringARB", "glGetNamedStringARB",
                "glGetNamedStringiARB", "glGetNamedStringivARB", "glIsNamedStringARB",
                "glNamedStringARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void legacyFixedFunctionExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glInsertEventMarkerEXT", "glPopGroupMarkerEXT", "glPushGroupMarkerEXT",
                "glLoadTransposeMatrixdARB", "glLoadTransposeMatrixfARB",
                "glMultTransposeMatrixdARB", "glMultTransposeMatrixfARB",
                "glEvaluateDepthValuesARB", "glFramebufferSampleLocationsfvARB",
                "glNamedFramebufferSampleLocationsfvARB",
                "glBufferPageCommitmentARB", "glNamedBufferPageCommitmentARB",
                "glNamedBufferPageCommitmentEXT",
                "glGetUniformBufferSizeEXT", "glGetUniformOffsetEXT", "glUniformBufferEXT",
                "glVertexBlendARB", "glWeightPointerARB", "glWeightbvARB", "glWeightdvARB",
                "glWeightfvARB", "glWeightivARB", "glWeightsvARB", "glWeightubvARB",
                "glWeightuivARB", "glWeightusvARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void arbGpuShaderInt64EntrypointsResolveThroughProvider() {
        for (String name : List.of("glGetUniformi64vARB", "glGetUniformui64vARB",
                "glGetnUniformi64vARB", "glGetnUniformui64vARB",
                "glProgramUniform1i64ARB", "glProgramUniform1i64vARB",
                "glProgramUniform1ui64ARB", "glProgramUniform1ui64vARB",
                "glProgramUniform2i64ARB", "glProgramUniform2i64vARB",
                "glProgramUniform2ui64ARB", "glProgramUniform2ui64vARB",
                "glProgramUniform3i64ARB", "glProgramUniform3i64vARB",
                "glProgramUniform3ui64ARB", "glProgramUniform3ui64vARB",
                "glProgramUniform4i64ARB", "glProgramUniform4i64vARB",
                "glProgramUniform4ui64ARB", "glProgramUniform4ui64vARB",
                "glUniform1i64ARB", "glUniform1i64vARB", "glUniform1ui64ARB",
                "glUniform1ui64vARB", "glUniform2i64ARB", "glUniform2i64vARB",
                "glUniform2ui64ARB", "glUniform2ui64vARB", "glUniform3i64ARB",
                "glUniform3i64vARB", "glUniform3ui64ARB", "glUniform3ui64vARB",
                "glUniform4i64ARB", "glUniform4i64vARB", "glUniform4ui64ARB",
                "glUniform4ui64vARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void smallRemainingExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glActiveProgramEXT", "glCreateShaderProgramEXT",
                "glUseShaderProgramEXT", "glTexStorage1DEXT", "glTexStorage2DEXT",
                "glTexStorage3DEXT", "glClearNamedBufferDataEXT",
                "glClearNamedBufferSubDataEXT", "glDrawArraysInstancedARB",
                "glDrawElementsInstancedARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void remainingTinyExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glMultiDrawArraysIndirectCountARB",
                "glMultiDrawElementsIndirectCountARB", "glVertexArrayVertexAttribDivisorEXT",
                "glVertexAttribDivisorARB", "glPointParameterfARB", "glPointParameterfvARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void sparseAndSyncExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glTexPageCommitmentARB", "glTexturePageCommitmentEXT",
                "glLockArraysEXT", "glUnlockArraysEXT", "glGetInteger64", "glGetSynci")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void debugLabelAndDrawExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glGetObjectLabelEXT", "glLabelObjectEXT",
                "glDrawArraysInstancedEXT", "glDrawElementsInstancedEXT",
                "glEGLImageTargetTexStorageEXT", "glEGLImageTargetTextureStorageEXT",
                "glDrawTextureNV", "glTextureBarrierNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void externalBufferBlitLayerAndProgramParameterEntrypointsResolveThroughProvider() {
        for (String name : List.of("glBufferStorageExternalEXT", "glNamedBufferStorageExternalEXT",
                "glBlitFramebufferLayerEXT", "glBlitFramebufferLayersEXT",
                "glProgramEnvParameters4fvEXT", "glProgramLocalParameters4fvEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void win32InteropImageStoreAndPointParameterEntrypointsResolveThroughProvider() {
        for (String name : List.of("glAcquireKeyedMutexWin32EXT", "glReleaseKeyedMutexWin32EXT",
                "glBindImageTextureEXT", "glMemoryBarrierEXT",
                "glImportSemaphoreWin32HandleEXT", "glImportSemaphoreWin32NameEXT",
                "glPointParameterfEXT", "glPointParameterfvEXT",
                "glImportMemoryWin32HandleEXT", "glImportMemoryWin32NameEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void remainingSingleExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glGetMultisamplef", "glTextureBufferRangeEXT",
                "glNamedBufferStorageEXT", "glMaxShaderCompilerThreadsKHR",
                "glBlendBarrierKHR", "glBlendEquationEXT",
                "glBlendFuncSeparateEXT", "glBlendColorEXT",
                "glBlendEquationSeparateEXT", "glTexBufferEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void syncWindowStencilAndFdExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glImportSyncEXT", "glWindowRectanglesEXT",
                "glActiveStencilFaceEXT", "glStencilClearTagEXT",
                "glFramebufferFetchBarrierEXT", "glProvokingVertexEXT",
                "glImportMemoryFdEXT", "glImportSemaphoreFdEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void rasterMultisampleFramebufferAndComputeExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glRasterSamplesEXT", "glPolygonOffsetClampEXT",
                "glRenderbufferStorageMultisampleEXT", "glBlitFramebufferEXT",
                "glDepthBoundsEXT", "glClampColorARB",
                "glDispatchComputeGroupSizeARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void remainingArbSingleEntrypointsResolveThroughProvider() {
        for (String name : List.of("glTexBufferARB", "glMinSampleShadingARB",
                "glPolygonOffsetClamp", "glMaxShaderCompilerThreadsARB",
                "glSampleCoverageARB", "glPrimitiveBoundingBoxARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void finalTinyArbExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glSpecializeShaderARB", "glDrawBuffersARB",
                "glCreateSyncFromCLeventARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void semaphoreExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glGetUnsignedBytevEXT", "glGetUnsignedBytei_vEXT",
                "glGenSemaphoresEXT", "glDeleteSemaphoresEXT", "glIsSemaphoreEXT",
                "glSemaphoreParameterui64vEXT", "glSemaphoreParameterui64EXT",
                "glGetSemaphoreParameterui64vEXT", "glGetSemaphoreParameterui64EXT",
                "glWaitSemaphoreEXT", "glSignalSemaphoreEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void memoryObjectExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glDeleteMemoryObjectsEXT", "glIsMemoryObjectEXT",
                "glCreateMemoryObjectsEXT", "glMemoryObjectParameterivEXT",
                "glMemoryObjectParameteriEXT", "glGetMemoryObjectParameterivEXT",
                "glGetMemoryObjectParameteriEXT", "glTexStorageMem1DEXT",
                "glTexStorageMem2DEXT", "glTexStorageMem2DMultisampleEXT",
                "glTexStorageMem3DEXT", "glTexStorageMem3DMultisampleEXT",
                "glBufferStorageMemEXT", "glTextureStorageMem1DEXT",
                "glTextureStorageMem2DEXT", "glTextureStorageMem2DMultisampleEXT",
                "glTextureStorageMem3DEXT", "glTextureStorageMem3DMultisampleEXT",
                "glNamedBufferStorageMemEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void bindlessTextureEntrypointsResolveThroughProvider() {
        for (String name : List.of("glGetTextureHandleARB",
                "glGetTextureSamplerHandleARB", "glMakeTextureHandleResidentARB",
                "glMakeTextureHandleNonResidentARB", "glGetImageHandleARB",
                "glMakeImageHandleResidentARB", "glMakeImageHandleNonResidentARB",
                "glUniformHandleui64ARB", "glUniformHandleui64vARB",
                "glProgramUniformHandleui64ARB", "glProgramUniformHandleui64vARB",
                "glIsTextureHandleResidentARB", "glIsImageHandleResidentARB",
                "glVertexAttribL1ui64ARB", "glVertexAttribL1ui64vARB",
                "glGetVertexAttribLui64vARB", "glGetVertexAttribLui64ARB")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void bindlessTextureProviderTracksResidentFakeHandles() {
        long getTextureHandle = GlFunctionRegistry.address("glGetTextureHandleARB");
        long makeTextureResident = GlFunctionRegistry.address("glMakeTextureHandleResidentARB");
        long makeTextureNonResident = GlFunctionRegistry.address("glMakeTextureHandleNonResidentARB");
        long isTextureResident = GlFunctionRegistry.address("glIsTextureHandleResidentARB");
        long getImageHandle = GlFunctionRegistry.address("glGetImageHandleARB");
        long makeImageResident = GlFunctionRegistry.address("glMakeImageHandleResidentARB");
        long makeImageNonResident = GlFunctionRegistry.address("glMakeImageHandleNonResidentARB");
        long isImageResident = GlFunctionRegistry.address("glIsImageHandleResidentARB");
        long getVertexAttrib = GlFunctionRegistry.address("glGetVertexAttribLui64vARB");

        long textureHandle = JNI.callJ(23, getTextureHandle);
        assertTrue(textureHandle != 0L, "fake texture handles must be non-zero so mod probes can cache them");
        assertFalse(JNI.callJZ(textureHandle, isTextureResident));
        JNI.callJV(textureHandle, makeTextureResident);
        assertTrue(JNI.callJZ(textureHandle, isTextureResident));
        JNI.callJV(textureHandle, makeTextureNonResident);
        assertFalse(JNI.callJZ(textureHandle, isTextureResident));

        long imageHandle = JNI.callJ(23, 1, true, 2, 0x8058, getImageHandle);
        assertTrue(imageHandle != 0L, "fake image handles must be non-zero so mod probes can cache them");
        assertFalse(JNI.callJZ(imageHandle, isImageResident));
        JNI.callJV(imageHandle, 0x88B9, makeImageResident);
        assertTrue(JNI.callJZ(imageHandle, isImageResident));
        JNI.callJV(imageHandle, makeImageNonResident);
        assertFalse(JNI.callJZ(imageHandle, isImageResident));

        LongBuffer value = MemoryUtil.memAllocLong(1);
        try {
            value.put(0, 0x1234L);
            JNI.callPV(0, 0, MemoryUtil.memAddress(value), getVertexAttrib);
            assertEquals(0L, value.get(0), "64-bit vertex attrib readback must return a deterministic zero");
        } finally {
            MemoryUtil.memFree(value);
        }
    }

    @Test
    void nvHalfFloatEntrypointsResolveThroughProvider() {
        for (String name : List.of("glVertex2hNV", "glVertex2hvNV",
                "glVertex3hNV", "glVertex3hvNV", "glVertex4hNV",
                "glVertex4hvNV", "glNormal3hNV", "glNormal3hvNV",
                "glColor3hNV", "glColor3hvNV", "glColor4hNV",
                "glColor4hvNV", "glTexCoord1hNV", "glTexCoord1hvNV",
                "glTexCoord2hNV", "glTexCoord2hvNV", "glTexCoord3hNV",
                "glTexCoord3hvNV", "glTexCoord4hNV", "glTexCoord4hvNV",
                "glMultiTexCoord1hNV", "glMultiTexCoord1hvNV",
                "glMultiTexCoord2hNV", "glMultiTexCoord2hvNV",
                "glMultiTexCoord3hNV", "glMultiTexCoord3hvNV",
                "glMultiTexCoord4hNV", "glMultiTexCoord4hvNV",
                "glFogCoordhNV", "glFogCoordhvNV",
                "glSecondaryColor3hNV", "glSecondaryColor3hvNV",
                "glVertexWeighthNV", "glVertexWeighthvNV",
                "glVertexAttrib1hNV", "glVertexAttrib1hvNV",
                "glVertexAttrib2hNV", "glVertexAttrib2hvNV",
                "glVertexAttrib3hNV", "glVertexAttrib3hvNV",
                "glVertexAttrib4hNV", "glVertexAttrib4hvNV",
                "glVertexAttribs1hvNV", "glVertexAttribs2hvNV",
                "glVertexAttribs3hvNV", "glVertexAttribs4hvNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvInt64ShaderEntrypointsResolveThroughProvider() {
        for (String name : List.of("glUniform1i64NV", "glUniform2i64NV",
                "glUniform3i64NV", "glUniform4i64NV",
                "glUniform1i64vNV", "glUniform2i64vNV",
                "glUniform3i64vNV", "glUniform4i64vNV",
                "glUniform1ui64NV", "glUniform2ui64NV",
                "glUniform3ui64NV", "glUniform4ui64NV",
                "glUniform1ui64vNV", "glUniform2ui64vNV",
                "glUniform3ui64vNV", "glUniform4ui64vNV",
                "glGetUniformi64vNV", "glGetUniformi64NV",
                "glGetUniformui64vNV", "glGetUniformui64NV",
                "glProgramUniform1i64NV", "glProgramUniform2i64NV",
                "glProgramUniform3i64NV", "glProgramUniform4i64NV",
                "glProgramUniform1i64vNV", "glProgramUniform2i64vNV",
                "glProgramUniform3i64vNV", "glProgramUniform4i64vNV",
                "glProgramUniform1ui64NV", "glProgramUniform2ui64NV",
                "glProgramUniform3ui64NV", "glProgramUniform4ui64NV",
                "glProgramUniform1ui64vNV", "glProgramUniform2ui64vNV",
                "glProgramUniform3ui64vNV", "glProgramUniform4ui64vNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvShaderBufferLoadEntrypointsResolveThroughProvider() {
        for (String name : List.of("glMakeBufferResidentNV",
                "glMakeBufferNonResidentNV", "glIsBufferResidentNV",
                "glMakeNamedBufferResidentNV",
                "glMakeNamedBufferNonResidentNV",
                "glIsNamedBufferResidentNV",
                "glGetBufferParameterui64vNV",
                "glGetBufferParameterui64NV",
                "glGetNamedBufferParameterui64vNV",
                "glGetNamedBufferParameterui64NV",
                "glGetIntegerui64vNV", "glGetIntegerui64NV",
                "glUniformui64NV", "glUniformui64vNV",
                "glGetUniformui64vNV", "glGetUniformui64NV",
                "glProgramUniformui64NV", "glProgramUniformui64vNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvInt64QueriesReturnDeterministicZeroDefaults() {
        long getUniformi64 = GlFunctionRegistry.address("glGetUniformi64NV");
        long getUniformi64v = GlFunctionRegistry.address("glGetUniformi64vNV");
        long getBufferParameter = GlFunctionRegistry.address("glGetBufferParameterui64NV");
        long getBufferParameterv = GlFunctionRegistry.address("glGetBufferParameterui64vNV");
        long isBufferResident = GlFunctionRegistry.address("glIsBufferResidentNV");

        assertTrue(getUniformi64 != 0L, "NV int64 uniform scalar query must have a callable trampoline address");
        assertTrue(getUniformi64v != 0L, "NV int64 uniform vector query must have a callable trampoline address");
        assertTrue(getBufferParameter != 0L, "NV buffer address scalar query must have a callable trampoline address");
        assertTrue(getBufferParameterv != 0L, "NV buffer address vector query must have a callable trampoline address");
        assertTrue(isBufferResident != 0L, "NV buffer residency query must have a callable trampoline address");

        assertEquals(0L, JNI.callJ(1, 2, getUniformi64));
        assertEquals(0L, JNI.callJ(0x8892, 0x8F1D, getBufferParameter));
        assertFalse(JNI.callZ(0x8892, isBufferResident));

        LongBuffer value = MemoryUtil.memAllocLong(1);
        try {
            value.put(0, 0x12345678L);
            JNI.callPV(1, 2, MemoryUtil.memAddress(value), getUniformi64v);
            assertEquals(0L, value.get(0), "NV int64 uniform vector readback must clear the caller buffer");

            value.put(0, 0x12345678L);
            JNI.callPV(0x8892, 0x8F1D, MemoryUtil.memAddress(value), getBufferParameterv);
            assertEquals(0L, value.get(0), "NV buffer address readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
        }
    }

    @Test
    void nvUtilityControlEntrypointsResolveThroughProvider() {
        for (String name : List.of("glBlendParameteriNV", "glBlendBarrierNV",
                "glBeginConditionalRenderNV", "glEndConditionalRenderNV",
                "glCopyImageSubDataNV", "glDepthRangedNV",
                "glClearDepthdNV", "glDepthBoundsdNV",
                "glPixelDataRangeNV", "glFlushPixelDataRangeNV",
                "glPrimitiveRestartNV", "glPrimitiveRestartIndexNV",
                "glFramebufferSampleLocationsfvNV",
                "glNamedFramebufferSampleLocationsfvNV",
                "glResolveDepthValuesNV",
                "glTexImage2DMultisampleCoverageNV",
                "glTexImage3DMultisampleCoverageNV",
                "glTextureImage2DMultisampleNV",
                "glTextureImage3DMultisampleNV",
                "glTextureImage2DMultisampleCoverageNV",
                "glTextureImage3DMultisampleCoverageNV",
                "glBeginTransformFeedbackNV", "glEndTransformFeedbackNV",
                "glTransformFeedbackAttribsNV", "glBindBufferRangeNV",
                "glBindBufferOffsetNV", "glBindBufferBaseNV",
                "glTransformFeedbackVaryingsNV", "glActiveVaryingNV",
                "glGetVaryingLocationNV", "glGetActiveVaryingNV",
                "glGetTransformFeedbackVaryingNV",
                "glTransformFeedbackStreamAttribsNV",
                "glVertexArrayRangeNV", "glFlushVertexArrayRangeNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvPathRenderingEntrypointsResolveThroughProvider() {
        for (String name : List.of("glPathCommandsNV", "glPathCoordsNV",
                "glPathSubCommandsNV", "glPathSubCoordsNV",
                "glPathStringNV", "glPathGlyphsNV", "glPathGlyphRangeNV",
                "glPathGlyphIndexArrayNV", "glPathMemoryGlyphIndexArrayNV",
                "glCopyPathNV", "glWeightPathsNV", "glInterpolatePathsNV",
                "glTransformPathNV", "glPathParameterivNV",
                "glPathParameteriNV", "glPathParameterfvNV",
                "glPathParameterfNV", "glPathDashArrayNV", "glGenPathsNV",
                "glDeletePathsNV", "glIsPathNV", "glPathStencilFuncNV",
                "glPathStencilDepthOffsetNV", "glStencilFillPathNV",
                "glStencilStrokePathNV", "glStencilFillPathInstancedNV",
                "glStencilStrokePathInstancedNV", "glPathCoverDepthFuncNV",
                "glPathColorGenNV", "glPathTexGenNV", "glPathFogGenNV",
                "glCoverFillPathNV", "glCoverStrokePathNV",
                "glCoverFillPathInstancedNV",
                "glCoverStrokePathInstancedNV",
                "glStencilThenCoverFillPathNV",
                "glStencilThenCoverStrokePathNV",
                "glStencilThenCoverFillPathInstancedNV",
                "glStencilThenCoverStrokePathInstancedNV",
                "glPathGlyphIndexRangeNV",
                "glProgramPathFragmentInputGenNV",
                "glGetPathParameterivNV", "glGetPathParameteriNV",
                "glGetPathParameterfvNV", "glGetPathParameterfNV",
                "glGetPathCommandsNV", "glGetPathCoordsNV",
                "glGetPathDashArrayNV", "glGetPathMetricsNV",
                "glGetPathMetricRangeNV", "glGetPathSpacingNV",
                "glGetPathColorGenivNV", "glGetPathColorGeniNV",
                "glGetPathColorGenfvNV", "glGetPathColorGenfNV",
                "glGetPathTexGenivNV", "glGetPathTexGeniNV",
                "glGetPathTexGenfvNV", "glGetPathTexGenfNV",
                "glIsPointInFillPathNV", "glIsPointInStrokePathNV",
                "glGetPathLengthNV", "glPointAlongPathNV",
                "glMatrixLoad3x2fNV", "glMatrixLoad3x3fNV",
                "glMatrixLoadTranspose3x3fNV", "glMatrixMult3x2fNV",
                "glMatrixMult3x3fNV", "glMatrixMultTranspose3x3fNV",
                "glGetProgramResourcefvNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvPathRenderingIdsAndQueriesReturnDeterministicDefaults() {
        long genPaths = GlFunctionRegistry.address("glGenPathsNV");
        long deletePaths = GlFunctionRegistry.address("glDeletePathsNV");
        long isPath = GlFunctionRegistry.address("glIsPathNV");
        long getPathParameteri = GlFunctionRegistry.address("glGetPathParameteriNV");
        long getPathParameteriv = GlFunctionRegistry.address("glGetPathParameterivNV");
        long getPathLength = GlFunctionRegistry.address("glGetPathLengthNV");

        assertTrue(genPaths != 0L, "NV path generation must have a callable trampoline address");
        assertTrue(deletePaths != 0L, "NV path deletion must have a callable trampoline address");
        assertTrue(isPath != 0L, "NV path query must have a callable trampoline address");
        assertTrue(getPathParameteri != 0L, "NV path scalar parameter query must have a callable trampoline address");
        assertTrue(getPathParameteriv != 0L, "NV path vector parameter query must have a callable trampoline address");
        assertTrue(getPathLength != 0L, "NV path length query must have a callable trampoline address");

        int firstPath = JNI.callI(3, genPaths);
        assertTrue(firstPath > 0, "NV path generation must return a non-zero first id");
        assertTrue(JNI.callZ(firstPath, isPath), "generated NV path id must be reported as valid");
        assertTrue(JNI.callZ(firstPath + 2, isPath), "generated NV path range must be reported as valid");
        assertFalse(JNI.callZ(firstPath + 3, isPath), "outside generated NV path range must remain invalid");

        assertEquals(0, JNI.callI(firstPath, 0x907D, getPathParameteri));
        assertEquals(0.0f, JNI.callF(firstPath, 0, 0, getPathLength));

        IntBuffer value = MemoryUtil.memAllocInt(1);
        try {
            value.put(0, 0x12345678);
            JNI.callPV(firstPath, 0x907D, MemoryUtil.memAddress(value), getPathParameteriv);
            assertEquals(0, value.get(0), "NV path parameter readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
        }

        JNI.callV(firstPath, 3, deletePaths);
        assertFalse(JNI.callZ(firstPath, isPath), "deleted NV path range must be invalidated");
        assertFalse(JNI.callZ(firstPath + 2, isPath), "deleted NV path range must be invalidated");
    }

    @Test
    void nvVertexAttribAndUnifiedMemoryEntrypointsResolveThroughProvider() {
        for (String name : List.of("glVertexAttribL1i64NV",
                "glVertexAttribL2i64NV", "glVertexAttribL3i64NV",
                "glVertexAttribL4i64NV", "glVertexAttribL1i64vNV",
                "glVertexAttribL2i64vNV", "glVertexAttribL3i64vNV",
                "glVertexAttribL4i64vNV", "glVertexAttribL1ui64NV",
                "glVertexAttribL2ui64NV", "glVertexAttribL3ui64NV",
                "glVertexAttribL4ui64NV", "glVertexAttribL1ui64vNV",
                "glVertexAttribL2ui64vNV", "glVertexAttribL3ui64vNV",
                "glVertexAttribL4ui64vNV", "glGetVertexAttribLi64vNV",
                "glGetVertexAttribLi64NV", "glGetVertexAttribLui64vNV",
                "glGetVertexAttribLui64NV", "glVertexAttribLFormatNV",
                "glBufferAddressRangeNV", "glVertexFormatNV",
                "glNormalFormatNV", "glColorFormatNV", "glIndexFormatNV",
                "glTexCoordFormatNV", "glEdgeFlagFormatNV",
                "glSecondaryColorFormatNV", "glFogCoordFormatNV",
                "glVertexAttribFormatNV", "glVertexAttribIFormatNV",
                "glGetIntegerui64i_vNV", "glGetIntegerui64iNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvVertexAttribAndUnifiedMemoryQueriesReturnDeterministicZeroDefaults() {
        long getVertexAttrib = GlFunctionRegistry.address("glGetVertexAttribLi64NV");
        long getVertexAttribv = GlFunctionRegistry.address("glGetVertexAttribLi64vNV");
        long getIntegerIndexed = GlFunctionRegistry.address("glGetIntegerui64iNV");
        long getIntegerIndexedv = GlFunctionRegistry.address("glGetIntegerui64i_vNV");

        assertTrue(getVertexAttrib != 0L, "NV vertex attrib scalar query must have a callable trampoline address");
        assertTrue(getVertexAttribv != 0L, "NV vertex attrib vector query must have a callable trampoline address");
        assertTrue(getIntegerIndexed != 0L, "NV unified memory scalar query must have a callable trampoline address");
        assertTrue(getIntegerIndexedv != 0L, "NV unified memory vector query must have a callable trampoline address");

        assertEquals(0L, JNI.callJ(0, 0, getVertexAttrib));
        assertEquals(0L, JNI.callJ(0x8F1D, 0, getIntegerIndexed));

        LongBuffer value = MemoryUtil.memAllocLong(1);
        try {
            value.put(0, 0x12345678L);
            JNI.callPV(0, 0, MemoryUtil.memAddress(value), getVertexAttribv);
            assertEquals(0L, value.get(0), "NV vertex attrib readback must clear the caller buffer");

            value.put(0, 0x12345678L);
            JNI.callPV(0x8F1D, 0, MemoryUtil.memAddress(value), getIntegerIndexedv);
            assertEquals(0L, value.get(0), "NV unified memory indexed readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
        }
    }

    @Test
    void nvBindlessTextureEntrypointsResolveThroughProvider() {
        for (String name : List.of("glGetTextureHandleNV",
                "glGetTextureSamplerHandleNV", "glMakeTextureHandleResidentNV",
                "glMakeTextureHandleNonResidentNV", "glGetImageHandleNV",
                "glMakeImageHandleResidentNV",
                "glMakeImageHandleNonResidentNV", "glUniformHandleui64NV",
                "glUniformHandleui64vNV", "glProgramUniformHandleui64NV",
                "glProgramUniformHandleui64vNV",
                "glIsTextureHandleResidentNV",
                "glIsImageHandleResidentNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvBindlessTextureProviderTracksResidentFakeHandles() {
        long getTextureHandle = GlFunctionRegistry.address("glGetTextureHandleNV");
        long makeTextureResident = GlFunctionRegistry.address("glMakeTextureHandleResidentNV");
        long makeTextureNonResident = GlFunctionRegistry.address("glMakeTextureHandleNonResidentNV");
        long isTextureResident = GlFunctionRegistry.address("glIsTextureHandleResidentNV");
        long getImageHandle = GlFunctionRegistry.address("glGetImageHandleNV");
        long makeImageResident = GlFunctionRegistry.address("glMakeImageHandleResidentNV");
        long makeImageNonResident = GlFunctionRegistry.address("glMakeImageHandleNonResidentNV");
        long isImageResident = GlFunctionRegistry.address("glIsImageHandleResidentNV");

        assertTrue(getTextureHandle != 0L, "NV texture handle query must have a callable trampoline address");
        assertTrue(getImageHandle != 0L, "NV image handle query must have a callable trampoline address");

        long textureHandle = JNI.callJ(23, getTextureHandle);
        assertTrue(textureHandle != 0L, "NV bindless texture handle must be deterministic and non-zero");
        assertFalse(JNI.callJZ(textureHandle, isTextureResident));
        JNI.callJV(textureHandle, makeTextureResident);
        assertTrue(JNI.callJZ(textureHandle, isTextureResident));
        JNI.callJV(textureHandle, makeTextureNonResident);
        assertFalse(JNI.callJZ(textureHandle, isTextureResident));

        long imageHandle = JNI.callJ(23, 1, true, 2, 0x8058, getImageHandle);
        assertTrue(imageHandle != 0L, "NV bindless image handle must be deterministic and non-zero");
        assertFalse(JNI.callJZ(imageHandle, isImageResident));
        JNI.callJV(imageHandle, 0x88B9, makeImageResident);
        assertTrue(JNI.callJZ(imageHandle, isImageResident));
        JNI.callJV(imageHandle, makeImageNonResident);
        assertFalse(JNI.callJZ(imageHandle, isImageResident));
    }

    @Test
    void nvFenceAndSmallControlEntrypointsResolveThroughProvider() {
        for (String name : List.of("glDeleteFencesNV", "glGenFencesNV",
                "glIsFenceNV", "glTestFenceNV", "glGetFenceivNV",
                "glGetFenceiNV", "glFinishFenceNV", "glSetFenceNV",
                "glAlphaToCoverageDitherControlNV",
                "glViewportPositionWScaleNV", "glSubpixelPrecisionBiasNV",
                "glConservativeRasterParameterfNV",
                "glConservativeRasterParameteriNV",
                "glGetMultisamplefvNV", "glSampleMaskIndexedNV",
                "glTexRenderbufferNV", "glFragmentCoverageColorNV",
                "glCoverageModulationTableNV",
                "glGetCoverageModulationTableNV",
                "glCoverageModulationNV",
                "glRenderbufferStorageMultisampleCoverageNV",
                "glPointParameteriNV", "glPointParameterivNV",
                "glQueryResourceNV", "glGenQueryResourceTagNV",
                "glDeleteQueryResourceTagNV", "glQueryResourceTagNV",
                "glScissorExclusiveArrayvNV", "glScissorExclusiveNV",
                "glCreateSemaphoresNV", "glSemaphoreParameterivNV",
                "glGetSemaphoreParameterivNV", "glViewportSwizzleNV",
                "glBeginConditionalRenderNVX", "glEndConditionalRenderNVX",
                "glCreateProgressFenceNVX", "glSignalSemaphoreui64NVX",
                "glWaitSemaphoreui64NVX", "glClientWaitSemaphoreui64NVX")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvFenceAndSmallQueriesReturnDeterministicDefaults() {
        long genFences = GlFunctionRegistry.address("glGenFencesNV");
        long deleteFences = GlFunctionRegistry.address("glDeleteFencesNV");
        long isFence = GlFunctionRegistry.address("glIsFenceNV");
        long testFence = GlFunctionRegistry.address("glTestFenceNV");
        long getFencei = GlFunctionRegistry.address("glGetFenceiNV");
        long getFenceiv = GlFunctionRegistry.address("glGetFenceivNV");
        long queryResource = GlFunctionRegistry.address("glQueryResourceNV");
        long getSemaphoreParameter = GlFunctionRegistry.address("glGetSemaphoreParameterivNV");

        assertTrue(genFences != 0L, "NV fence generation must have a callable trampoline address");
        assertTrue(isFence != 0L, "NV fence query must have a callable trampoline address");
        assertTrue(queryResource != 0L, "NV resource query must have a callable trampoline address");

        IntBuffer value = MemoryUtil.memAllocInt(1);
        try {
            value.put(0, 0);
            JNI.callPV(1, MemoryUtil.memAddress(value), genFences);
            int fence = value.get(0);
            assertTrue(fence > 0, "NV fence generation must return a non-zero id");
            assertTrue(JNI.callZ(fence, isFence), "generated NV fence id must be reported as valid");
            assertTrue(JNI.callZ(fence, testFence), "generated NV fence must test as complete");
            assertEquals(1, JNI.callI(fence, 0x84F3, getFencei));

            value.put(0, 0x12345678);
            JNI.callPV(fence, 0x84F3, MemoryUtil.memAddress(value), getFenceiv);
            assertEquals(1, value.get(0), "NV fence status readback must report complete");

            value.put(0, 0x12345678);
            assertEquals(0, JNI.callPI(0, 0, 1, MemoryUtil.memAddress(value), queryResource));
            assertEquals(0, value.get(0), "NV resource query must clear the caller buffer");

            value.put(0, 0x12345678);
            JNI.callPV(0, 0x9595, MemoryUtil.memAddress(value), getSemaphoreParameter);
            assertEquals(0, value.get(0), "NV timeline semaphore query must clear the caller buffer");

            value.put(0, fence);
            JNI.callPV(1, MemoryUtil.memAddress(value), deleteFences);
            assertFalse(JNI.callZ(fence, isFence), "deleted NV fence id must be invalidated");
        } finally {
            MemoryUtil.memFree(value);
        }
    }

    @Test
    void nvCommandMulticastAndDrawEntrypointsResolveThroughProvider() {
        for (String name : List.of("glCreateStatesNV", "glDeleteStatesNV",
                "glIsStateNV", "glStateCaptureNV", "glGetCommandHeaderNV",
                "glGetStageIndexNV", "glDrawCommandsNV",
                "glDrawCommandsAddressNV", "glDrawCommandsStatesNV",
                "glDrawCommandsStatesAddressNV", "glCreateCommandListsNV",
                "glDeleteCommandListsNV", "glIsCommandListNV",
                "glListDrawCommandsStatesClientNV",
                "glCommandListSegmentsNV", "glCompileCommandListNV",
                "glCallCommandListNV", "glRenderGpuMaskNV",
                "glMulticastBufferSubDataNV",
                "glMulticastCopyBufferSubDataNV",
                "glMulticastCopyImageSubDataNV",
                "glMulticastBlitFramebufferNV",
                "glMulticastFramebufferSampleLocationsfvNV",
                "glMulticastBarrierNV", "glMulticastWaitSyncNV",
                "glMulticastGetQueryObjectivNV",
                "glMulticastGetQueryObjectiNV",
                "glMulticastGetQueryObjectuivNV",
                "glMulticastGetQueryObjectuiNV",
                "glMulticastGetQueryObjecti64vNV",
                "glMulticastGetQueryObjecti64NV",
                "glMulticastGetQueryObjectui64vNV",
                "glMulticastGetQueryObjectui64NV",
                "glBindTransformFeedbackNV",
                "glDeleteTransformFeedbacksNV",
                "glGenTransformFeedbacksNV",
                "glIsTransformFeedbackNV",
                "glPauseTransformFeedbackNV",
                "glResumeTransformFeedbackNV",
                "glDrawTransformFeedbackNV",
                "glMultiDrawArraysIndirectBindlessNV",
                "glMultiDrawElementsIndirectBindlessNV",
                "glMultiDrawArraysIndirectBindlessCountNV",
                "glMultiDrawElementsIndirectBindlessCountNV",
                "glDrawMeshTasksNV", "glDrawMeshTasksIndirectNV",
                "glMultiDrawMeshTasksIndirectNV",
                "glMultiDrawMeshTasksIndirectCountNV",
                "glDrawVkImageNV", "glGetVkProcAddrNV",
                "glWaitVkSemaphoreNV", "glSignalVkSemaphoreNV",
                "glSignalVkFenceNV")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void nvCommandListAndMulticastQueriesReturnDeterministicDefaults() {
        long createStates = GlFunctionRegistry.address("glCreateStatesNV");
        long deleteStates = GlFunctionRegistry.address("glDeleteStatesNV");
        long isState = GlFunctionRegistry.address("glIsStateNV");
        long createCommandLists = GlFunctionRegistry.address("glCreateCommandListsNV");
        long deleteCommandLists = GlFunctionRegistry.address("glDeleteCommandListsNV");
        long isCommandList = GlFunctionRegistry.address("glIsCommandListNV");
        long multicastGetQueryObjectiv = GlFunctionRegistry.address("glMulticastGetQueryObjectivNV");
        long multicastGetQueryObjecti = GlFunctionRegistry.address("glMulticastGetQueryObjectiNV");
        long multicastGetQueryObjecti64v = GlFunctionRegistry.address("glMulticastGetQueryObjecti64vNV");

        assertTrue(createStates != 0L, "NV command state generation must have a callable trampoline address");
        assertTrue(createCommandLists != 0L, "NV command list generation must have a callable trampoline address");
        assertTrue(multicastGetQueryObjectiv != 0L, "NV multicast query vector readback must have a callable trampoline address");

        IntBuffer intValue = MemoryUtil.memAllocInt(1);
        LongBuffer longValue = MemoryUtil.memAllocLong(1);
        try {
            intValue.put(0, 0);
            JNI.callPV(1, MemoryUtil.memAddress(intValue), createStates);
            int state = intValue.get(0);
            assertTrue(state > 0, "NV command state generation must return a non-zero id");
            assertTrue(JNI.callZ(state, isState), "generated NV command state must be valid");

            intValue.put(0, state);
            JNI.callPV(1, MemoryUtil.memAddress(intValue), deleteStates);
            assertFalse(JNI.callZ(state, isState), "deleted NV command state must be invalidated");

            intValue.put(0, 0);
            JNI.callPV(1, MemoryUtil.memAddress(intValue), createCommandLists);
            int list = intValue.get(0);
            assertTrue(list > 0, "NV command list generation must return a non-zero id");
            assertTrue(JNI.callZ(list, isCommandList), "generated NV command list must be valid");

            intValue.put(0, list);
            JNI.callPV(1, MemoryUtil.memAddress(intValue), deleteCommandLists);
            assertFalse(JNI.callZ(list, isCommandList), "deleted NV command list must be invalidated");

            intValue.put(0, 0x12345678);
            JNI.callPV(0, 0, 0, MemoryUtil.memAddress(intValue), multicastGetQueryObjectiv);
            assertEquals(0, intValue.get(0), "NV multicast int query readback must clear the caller buffer");
            assertEquals(0, JNI.callI(0, 0, 0, multicastGetQueryObjecti));

            longValue.put(0, 0x12345678L);
            JNI.callPV(0, 0, 0, MemoryUtil.memAddress(longValue), multicastGetQueryObjecti64v);
            assertEquals(0L, longValue.get(0), "NV multicast int64 query readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(intValue);
            MemoryUtil.memFree(longValue);
        }
    }

    @Test
    void finalNvMemoryShadingAndMulticastEntrypointsResolveThroughProvider() {
        for (String name : List.of("glAsyncCopyBufferSubDataNVX",
                "glAsyncCopyImageSubDataNVX", "glBindShadingRateImageNV",
                "glBufferAttachMemoryNV", "glBufferPageCommitmentMemNV",
                "glGetInternalformatSampleivNV",
                "glGetMemoryObjectDetachedResourcesuivNV",
                "glGetShadingRateImagePaletteNV",
                "glGetShadingRateSampleLocationivNV",
                "glMulticastScissorArrayvNVX",
                "glMulticastViewportArrayvNVX",
                "glMulticastViewportPositionWScaleNVX",
                "glNamedBufferAttachMemoryNV",
                "glNamedBufferPageCommitmentMemNV",
                "glResetMemoryObjectParameterNV",
                "glShadingRateImageBarrierNV",
                "glShadingRateImagePaletteNV",
                "glShadingRateSampleOrderCustomNV",
                "glShadingRateSampleOrderNV", "glTexAttachMemoryNV",
                "glTexPageCommitmentMemNV", "glTextureAttachMemoryNV",
                "glTexturePageCommitmentMemNV", "glUploadGpuMaskNVX")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void finalNvMemoryAndShadingQueriesReturnDeterministicDefaults() {
        long getMemoryResources = GlFunctionRegistry.address("glGetMemoryObjectDetachedResourcesuivNV");
        long getInternalformatSample = GlFunctionRegistry.address("glGetInternalformatSampleivNV");
        long getShadingPalette = GlFunctionRegistry.address("glGetShadingRateImagePaletteNV");
        long getShadingSampleLocation = GlFunctionRegistry.address("glGetShadingRateSampleLocationivNV");
        long asyncCopyImage = GlFunctionRegistry.address("glAsyncCopyImageSubDataNVX");
        long asyncCopyBuffer = GlFunctionRegistry.address("glAsyncCopyBufferSubDataNVX");

        assertTrue(getMemoryResources != 0L, "NV memory resource query must have a callable trampoline address");
        assertTrue(getInternalformatSample != 0L, "NV internalformat sample query must have a callable trampoline address");
        assertTrue(asyncCopyImage != 0L, "NVX async image copy must have a callable trampoline address");
        assertTrue(asyncCopyBuffer != 0L, "NVX async buffer copy must have a callable trampoline address");

        IntBuffer value = MemoryUtil.memAllocInt(1);
        try {
            value.put(0, 0x12345678);
            JNI.callPV(0, 0, 0, 1, MemoryUtil.memAddress(value), getMemoryResources);
            assertEquals(0, value.get(0), "NV detached memory resource query must clear the caller buffer");

            value.put(0, 0x12345678);
            JNI.callPV(0, 0, 0, 0, 1, MemoryUtil.memAddress(value), getInternalformatSample);
            assertEquals(0, value.get(0), "NV internalformat sample query must clear the caller buffer");

            value.put(0, 0x12345678);
            JNI.callPV(0, 0, MemoryUtil.memAddress(value), getShadingPalette);
            assertEquals(0, value.get(0), "NV shading-rate palette query must clear the caller buffer");

            value.put(0, 0x12345678);
            JNI.callPV(0, 0, 0, MemoryUtil.memAddress(value), getShadingSampleLocation);
            assertEquals(0, value.get(0), "NV shading-rate sample query must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
        }
    }

    @Test
    void amdVendorExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glBeginPerfMonitorAMD",
                "glBlendEquationIndexedAMD",
                "glBlendEquationSeparateIndexedAMD",
                "glBlendFuncIndexedAMD",
                "glBlendFuncSeparateIndexedAMD",
                "glDebugMessageCallbackAMD",
                "glDebugMessageEnableAMD",
                "glDebugMessageInsertAMD",
                "glDeletePerfMonitorsAMD",
                "glEndPerfMonitorAMD",
                "glGenPerfMonitorsAMD",
                "glGetDebugMessageLogAMD",
                "glGetPerfMonitorCounterDataAMD",
                "glGetPerfMonitorCounterInfoAMD",
                "glGetPerfMonitorCounterStringAMD",
                "glGetPerfMonitorCountersAMD",
                "glGetPerfMonitorGroupStringAMD",
                "glGetPerfMonitorGroupsAMD",
                "glNamedRenderbufferStorageMultisampleAdvancedAMD",
                "glQueryObjectParameteruiAMD",
                "glRenderbufferStorageMultisampleAdvancedAMD",
                "glSelectPerfMonitorCountersAMD",
                "glSetMultisamplefvAMD",
                "glStencilOpValueAMD",
                "glTessellationFactorAMD",
                "glTessellationModeAMD",
                "glTexStorageSparseAMD",
                "glTextureStorageSparseAMD",
                "glVertexAttribParameteriAMD")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void amdPerformanceMonitorQueriesReturnDeterministicDefaults() {
        long genPerfMonitors = GlFunctionRegistry.address("glGenPerfMonitorsAMD");
        long deletePerfMonitors = GlFunctionRegistry.address("glDeletePerfMonitorsAMD");
        long getGroups = GlFunctionRegistry.address("glGetPerfMonitorGroupsAMD");
        long getCounterInfo = GlFunctionRegistry.address("glGetPerfMonitorCounterInfoAMD");

        assertTrue(genPerfMonitors != 0L, "AMD perf monitor generation must have a callable trampoline address");
        assertTrue(getGroups != 0L, "AMD perf monitor group query must have a callable trampoline address");

        IntBuffer value = MemoryUtil.memAllocInt(1);
        IntBuffer groups = MemoryUtil.memAllocInt(1);
        try {
            value.put(0, 0);
            JNI.callPV(1, MemoryUtil.memAddress(value), genPerfMonitors);
            int monitor = value.get(0);
            assertTrue(monitor > 0, "AMD perf monitor generation must return a non-zero id");

            value.put(0, 0x12345678);
            groups.put(0, 0x12345678);
            JNI.callPPV(MemoryUtil.memAddress(value), 1, MemoryUtil.memAddress(groups), getGroups);
            assertEquals(0, value.get(0), "AMD perf monitor group count must be zeroed");
            assertEquals(0, groups.get(0), "AMD perf monitor group list must be zeroed");

            value.put(0, 0x12345678);
            JNI.callPV(0, 0, 0, MemoryUtil.memAddress(value), getCounterInfo);
            assertEquals(0, value.get(0), "AMD perf monitor counter info must clear the caller buffer");

            value.put(0, monitor);
            JNI.callPV(1, MemoryUtil.memAddress(value), deletePerfMonitors);
        } finally {
            MemoryUtil.memFree(value);
            MemoryUtil.memFree(groups);
        }
    }

    @Test
    void intelVendorExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glApplyFramebufferAttachmentCMAAINTEL",
                "glBeginPerfQueryINTEL",
                "glCreatePerfQueryINTEL",
                "glDeletePerfQueryINTEL",
                "glEndPerfQueryINTEL",
                "glGetFirstPerfQueryIdINTEL",
                "glGetNextPerfQueryIdINTEL",
                "glGetPerfCounterInfoINTEL",
                "glGetPerfQueryDataINTEL",
                "glGetPerfQueryIdByNameINTEL",
                "glGetPerfQueryInfoINTEL",
                "glMapTexture2DINTEL",
                "glSyncTextureINTEL",
                "glUnmapTexture2DINTEL")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void intelPerformanceAndTextureQueriesReturnDeterministicDefaults() {
        long createPerfQuery = GlFunctionRegistry.address("glCreatePerfQueryINTEL");
        long deletePerfQuery = GlFunctionRegistry.address("glDeletePerfQueryINTEL");
        long getFirstPerfQuery = GlFunctionRegistry.address("glGetFirstPerfQueryIdINTEL");
        long getNextPerfQuery = GlFunctionRegistry.address("glGetNextPerfQueryIdINTEL");
        long mapTexture = GlFunctionRegistry.address("glMapTexture2DINTEL");

        assertTrue(createPerfQuery != 0L, "INTEL perf query creation must have a callable trampoline address");
        assertTrue(mapTexture != 0L, "INTEL texture mapping must have a callable trampoline address");

        IntBuffer value = MemoryUtil.memAllocInt(1);
        IntBuffer second = MemoryUtil.memAllocInt(1);
        try {
            value.put(0, 0);
            JNI.callPV(0, MemoryUtil.memAddress(value), createPerfQuery);
            int query = value.get(0);
            assertTrue(query > 0, "INTEL perf query creation must return a non-zero id");

            value.put(0, 0x12345678);
            JNI.callPV(MemoryUtil.memAddress(value), getFirstPerfQuery);
            assertEquals(0, value.get(0), "INTEL first perf query id must be zero when unsupported");

            value.put(0, 0x12345678);
            JNI.callPV(0, MemoryUtil.memAddress(value), getNextPerfQuery);
            assertEquals(0, value.get(0), "INTEL next perf query id must be zero when unsupported");

            value.put(0, 0x12345678);
            second.put(0, 0x12345678);
            assertEquals(0L, JNI.callPPP(0, 0, 0, MemoryUtil.memAddress(value),
                    MemoryUtil.memAddress(second), mapTexture));
            assertEquals(0, value.get(0), "INTEL texture map stride must be zeroed");
            assertEquals(0, second.get(0), "INTEL texture map layout must be zeroed");

            JNI.callV(query, deletePerfQuery);
        } finally {
            MemoryUtil.memFree(value);
            MemoryUtil.memFree(second);
        }
    }

    @Test
    void tinyVendorExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glFramebufferParameteriMESA",
                "glGetFramebufferParameterivMESA",
                "glFramebufferTextureMultiviewOVR",
                "glNamedFramebufferTextureMultiviewOVR",
                "glFrameTerminatorGREMEDY",
                "glStringMarkerGREMEDY")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void mesaFramebufferParameterQueryReturnsDeterministicDefault() {
        long getFramebufferParameter = GlFunctionRegistry.address("glGetFramebufferParameterivMESA");

        assertTrue(getFramebufferParameter != 0L, "MESA framebuffer parameter query must have a callable trampoline address");

        IntBuffer value = MemoryUtil.memAllocInt(1);
        try {
            value.put(0, 0x12345678);
            JNI.callPV(0, 0, MemoryUtil.memAddress(value), getFramebufferParameter);
            assertEquals(0, value.get(0), "MESA framebuffer parameter query must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
        }
    }

    @Test
    void legacyFixedFunctionDirectProviderEntrypointsResolve() {
        for (String name : List.of("glBegin", "glEnd", "glVertex2f",
                "glVertex3f", "glVertex4f", "glColor3f", "glColor4f",
                "glNormal3f", "glTexCoord1f", "glTexCoord2f",
                "glTexCoord3f", "glTexCoord4f", "glMultiTexCoord1f",
                "glMultiTexCoord2f", "glMultiTexCoord3f",
                "glMultiTexCoord4f", "glMatrixMode", "glPushMatrix",
                "glPopMatrix", "glLoadIdentity", "glTranslatef",
                "glRotatef", "glScalef", "glOrtho", "glFrustum",
                "glGenLists", "glDeleteLists", "glIsList", "glNewList",
                "glEndList", "glCallList", "glCallLists", "glListBase",
                "glRenderMode", "glShadeModel", "glAlphaFunc", "glFogf",
                "glFogi", "glFogfv", "glFogiv", "glLightf", "glLighti",
                "glLightfv", "glLightiv", "glMaterialf", "glMateriali",
                "glMaterialfv", "glMaterialiv", "glTexEnvf", "glTexEnvi",
                "glTexEnvfv", "glTexEnviv", "glTexGenf", "glTexGeni",
                "glTexGenfv", "glTexGeniv", "glGetTexEnviv",
                "glGetTexEnvfv", "glGetTexGeniv", "glGetTexGenfv",
                "glRasterPos2f", "glRasterPos3f", "glRasterPos4f",
                "glRectf", "glWindowPos2f", "glWindowPos3f")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void legacyDisplayListsAndFixedFunctionQueriesReturnDeterministicDefaults() {
        long genLists = GlFunctionRegistry.address("glGenLists");
        long deleteLists = GlFunctionRegistry.address("glDeleteLists");
        long isList = GlFunctionRegistry.address("glIsList");
        long renderMode = GlFunctionRegistry.address("glRenderMode");
        long getTexEnviv = GlFunctionRegistry.address("glGetTexEnviv");

        assertTrue(genLists != 0L, "legacy display-list generation must have a callable trampoline address");
        assertTrue(getTexEnviv != 0L, "legacy texture-env query must have a callable trampoline address");

        int first = JNI.callI(3, genLists);
        assertTrue(first > 0, "legacy display-list generation must return a non-zero id");
        assertTrue(JNI.callZ(first, isList), "generated legacy display-list id must be valid");
        assertTrue(JNI.callZ(first + 2, isList), "generated legacy display-list range must be valid");
        assertFalse(JNI.callZ(first + 3, isList), "outside legacy display-list range must remain invalid");
        assertEquals(0, JNI.callI(0x1C00, renderMode), "legacy render mode must return deterministic zero");

        IntBuffer value = MemoryUtil.memAllocInt(1);
        try {
            value.put(0, 0x12345678);
            JNI.callPV(0x2300, 0x2200, MemoryUtil.memAddress(value), getTexEnviv);
            assertEquals(0, value.get(0), "legacy texture-env query must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
        }

        JNI.callV(first, 3, deleteLists);
        assertFalse(JNI.callZ(first, isList), "deleted legacy display-list id must be invalidated");
        assertFalse(JNI.callZ(first + 2, isList), "deleted legacy display-list range must be invalidated");
    }

    @Test
    void remainingLegacyDirectProviderEntrypointsResolve() {
        for (String name : List.of("glAccum", "glAreTexturesResident",
                "glArrayElement", "glBitmap", "glClearAccum",
                "glClearIndex", "glClientActiveTexture", "glClipPlane",
                "glColor3ub", "glColor3ubv", "glColor3ui",
                "glColor3uiv", "glColor3us", "glColor3usv",
                "glColor4ub", "glColor4ubv", "glColor4ui",
                "glColor4uiv", "glColor4us", "glColor4usv",
                "glColorMaterial", "glColorPointer", "glCopyPixels",
                "glDisableClientState", "glDrawPixels", "glEdgeFlag",
                "glEdgeFlagPointer", "glEdgeFlagv",
                "glEnableClientState", "glEvalCoord1d",
                "glEvalCoord1dv", "glEvalCoord1f", "glEvalCoord1fv",
                "glEvalCoord2d", "glEvalCoord2dv", "glEvalCoord2f",
                "glEvalCoord2fv", "glEvalMesh1", "glEvalMesh2",
                "glEvalPoint1", "glEvalPoint2", "glFeedbackBuffer",
                "glFogCoordPointer", "glFogCoordd", "glFogCoorddv",
                "glFogCoordf", "glFogCoordfv", "glGetClipPlane",
                "glGetLightfv", "glGetLightiv", "glGetMapdv",
                "glGetMapfv", "glGetMapiv", "glGetMaterialfv",
                "glGetMaterialiv", "glGetPixelMapfv",
                "glGetPixelMapuiv", "glGetPixelMapusv",
                "glGetPolygonStipple", "glGetTexGendv", "glIndexMask",
                "glIndexPointer", "glIndexd", "glIndexdv", "glIndexf",
                "glIndexfv", "glIndexi", "glIndexiv", "glIndexs",
                "glIndexsv", "glIndexub", "glIndexubv", "glInitNames",
                "glInterleavedArrays", "glLineStipple", "glLoadName",
                "glMap1d", "glMap1f", "glMap2d", "glMap2f",
                "glMapGrid1d", "glMapGrid1f", "glMapGrid2d",
                "glMapGrid2f", "glMultiDrawArraysIndirectCount",
                "glMultiDrawElementsIndirectCount", "glNormalPointer",
                "glPassThrough", "glPixelMapfv", "glPixelMapuiv",
                "glPixelMapusv", "glPixelTransferf",
                "glPixelTransferi", "glPixelZoom", "glPolygonStipple",
                "glPopAttrib", "glPopClientAttrib", "glPopName",
                "glPrioritizeTextures", "glPushAttrib",
                "glPushClientAttrib", "glPushName", "glRasterPos2d",
                "glRasterPos2dv", "glRasterPos2s", "glRasterPos2sv",
                "glRasterPos3d", "glRasterPos3dv", "glRasterPos3s",
                "glRasterPos3sv", "glRasterPos4d", "glRasterPos4dv",
                "glRasterPos4s", "glRasterPos4sv", "glRectd",
                "glRectdv", "glRects", "glRectsv",
                "glSecondaryColor3b", "glSecondaryColor3bv",
                "glSecondaryColor3d", "glSecondaryColor3dv",
                "glSecondaryColor3f", "glSecondaryColor3fv",
                "glSecondaryColor3i", "glSecondaryColor3iv",
                "glSecondaryColor3s", "glSecondaryColor3sv",
                "glSecondaryColor3ub", "glSecondaryColor3ubv",
                "glSecondaryColor3ui", "glSecondaryColor3uiv",
                "glSecondaryColor3us", "glSecondaryColor3usv",
                "glSecondaryColorPointer", "glSelectBuffer",
                "glSpecializeShader", "glTexCoordPointer", "glTexGend",
                "glTexGendv", "glVertexPointer", "glWindowPos2d",
                "glWindowPos2dv", "glWindowPos2s", "glWindowPos2sv",
                "glWindowPos3d", "glWindowPos3dv", "glWindowPos3s",
                "glWindowPos3sv")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void remainingLegacyQueriesReturnDeterministicDefaults() {
        long areTexturesResident = GlFunctionRegistry.address("glAreTexturesResident");
        long getLightiv = GlFunctionRegistry.address("glGetLightiv");
        long getClipPlane = GlFunctionRegistry.address("glGetClipPlane");

        assertTrue(areTexturesResident != 0L, "legacy texture residency query must have a callable trampoline address");
        assertTrue(getLightiv != 0L, "legacy light query must have a callable trampoline address");

        IntBuffer value = MemoryUtil.memAllocInt(1);
        ByteBuffer residency = MemoryUtil.memAlloc(1);
        LongBuffer doubleBits = MemoryUtil.memAllocLong(4);
        try {
            residency.put(0, (byte) 0x7f);
            assertFalse(JNI.callPPZ(1, 0L, MemoryUtil.memAddress(residency), areTexturesResident));
            assertEquals(0, residency.get(0), "legacy texture residency query must clear the caller buffer");

            value.put(0, 0x12345678);
            JNI.callPV(0, 0, MemoryUtil.memAddress(value), getLightiv);
            assertEquals(0, value.get(0), "legacy light query must clear the caller buffer");

            doubleBits.put(0, 0x12345678L);
            JNI.callPV(0, MemoryUtil.memAddress(doubleBits), getClipPlane);
            assertEquals(0L, doubleBits.get(0), "legacy clip-plane query must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
            MemoryUtil.memFree(residency);
            MemoryUtil.memFree(doubleBits);
        }
    }

    @Test
    void imagingExtensionEntrypointsResolveThroughProvider() {
        for (String name : List.of("glColorSubTable", "glColorTable",
                "glColorTableParameterfv", "glColorTableParameteriv",
                "glConvolutionFilter1D", "glConvolutionFilter2D",
                "glConvolutionParameterf", "glConvolutionParameterfv",
                "glConvolutionParameteri", "glConvolutionParameteriv",
                "glCopyColorSubTable", "glCopyColorTable",
                "glCopyConvolutionFilter1D", "glCopyConvolutionFilter2D",
                "glGetColorTable", "glGetColorTableParameterf",
                "glGetColorTableParameterfv", "glGetColorTableParameteri",
                "glGetColorTableParameteriv", "glGetConvolutionFilter",
                "glGetConvolutionParameterf", "glGetConvolutionParameterfv",
                "glGetConvolutionParameteri", "glGetConvolutionParameteriv",
                "glGetHistogram", "glGetHistogramParameterf",
                "glGetHistogramParameterfv", "glGetHistogramParameteri",
                "glGetHistogramParameteriv", "glGetMinmax",
                "glGetMinmaxParameterf", "glGetMinmaxParameterfv",
                "glGetMinmaxParameteri", "glGetMinmaxParameteriv",
                "glGetSeparableFilter", "glHistogram", "glMinmax",
                "glResetHistogram", "glResetMinmax", "glSeparableFilter2D")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void imagingExtensionQueriesReturnDeterministicZeroDefaults() {
        long getColorTableParameteri = GlFunctionRegistry.address("glGetColorTableParameteri");
        long getConvolutionParameteriv = GlFunctionRegistry.address("glGetConvolutionParameteriv");
        long getHistogramParameteri = GlFunctionRegistry.address("glGetHistogramParameteri");

        assertTrue(getColorTableParameteri != 0L, "color table scalar query must have a callable trampoline address");
        assertTrue(getConvolutionParameteriv != 0L, "convolution parameter query must have a callable trampoline address");
        assertTrue(getHistogramParameteri != 0L, "histogram scalar query must have a callable trampoline address");

        assertEquals(0, JNI.callI(0x80D0, 0x80D7, getColorTableParameteri));
        assertEquals(0, JNI.callI(0x8024, 0x8026, getHistogramParameteri));

        LongBuffer value = MemoryUtil.memAllocLong(1);
        try {
            value.put(0, 0x12345678L);
            JNI.callPV(0x8010, 0x8013, MemoryUtil.memAddress(value), getConvolutionParameteriv);
            assertEquals(0L, value.get(0), "convolution parameter readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
        }
    }

    @Test
    void directStateAccessObjectEntrypointsResolveThroughProvider() {
        for (String name : List.of("glNamedBufferDataEXT",
                "glNamedBufferSubDataEXT", "glMapNamedBufferEXT",
                "glUnmapNamedBufferEXT", "glGetNamedBufferParameterivEXT",
                "glGetNamedBufferParameteriEXT", "glGetNamedBufferSubDataEXT",
                "glMapNamedBufferRangeEXT", "glFlushMappedNamedBufferRangeEXT",
                "glNamedCopyBufferSubDataEXT", "glNamedRenderbufferStorageEXT",
                "glGetNamedRenderbufferParameterivEXT",
                "glGetNamedRenderbufferParameteriEXT",
                "glNamedRenderbufferStorageMultisampleEXT",
                "glNamedRenderbufferStorageMultisampleCoverageEXT",
                "glCheckNamedFramebufferStatusEXT",
                "glNamedFramebufferTexture1DEXT",
                "glNamedFramebufferTexture2DEXT",
                "glNamedFramebufferTexture3DEXT",
                "glNamedFramebufferRenderbufferEXT",
                "glGetNamedFramebufferAttachmentParameterivEXT",
                "glGetNamedFramebufferAttachmentParameteriEXT",
                "glFramebufferDrawBufferEXT", "glFramebufferDrawBuffersEXT",
                "glFramebufferReadBufferEXT", "glGetFramebufferParameterivEXT",
                "glGetFramebufferParameteriEXT", "glNamedFramebufferTextureEXT",
                "glNamedFramebufferTextureLayerEXT",
                "glNamedFramebufferTextureFaceEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void directStateAccessFramebufferQueriesReturnDeterministicDefaults() {
        long checkStatus = GlFunctionRegistry.address("glCheckNamedFramebufferStatusEXT");
        long getAttachment = GlFunctionRegistry.address("glGetNamedFramebufferAttachmentParameteriEXT");

        assertTrue(checkStatus != 0L, "named framebuffer status must have a callable trampoline address");
        assertTrue(getAttachment != 0L, "named framebuffer attachment query must have a callable trampoline address");
        assertEquals(0x8CD5, JNI.callI(0, 0x8D40, checkStatus));
        assertEquals(0, JNI.callI(0, 0, 0, getAttachment));
    }

    @Test
    void directStateAccessTextureEntrypointsResolveThroughProvider() {
        for (String name : List.of("glTextureParameteriEXT",
                "glTextureParameterivEXT", "glTextureParameterfEXT",
                "glTextureParameterfvEXT", "glTextureImage1DEXT",
                "glTextureImage2DEXT", "glTextureImage3DEXT",
                "glTextureSubImage1DEXT", "glTextureSubImage2DEXT",
                "glTextureSubImage3DEXT", "glCopyTextureImage1DEXT",
                "glCopyTextureImage2DEXT", "glCopyTextureSubImage1DEXT",
                "glCopyTextureSubImage2DEXT", "glCopyTextureSubImage3DEXT",
                "glGetTextureImageEXT", "glGetTextureParameterfvEXT",
                "glGetTextureParameterfEXT", "glGetTextureParameterivEXT",
                "glGetTextureParameteriEXT", "glGetTextureLevelParameterfvEXT",
                "glGetTextureLevelParameterfEXT", "glGetTextureLevelParameterivEXT",
                "glGetTextureLevelParameteriEXT", "glCompressedTextureImage1DEXT",
                "glCompressedTextureImage2DEXT", "glCompressedTextureImage3DEXT",
                "glCompressedTextureSubImage1DEXT",
                "glCompressedTextureSubImage2DEXT",
                "glCompressedTextureSubImage3DEXT", "glTextureBufferEXT",
                "glTextureParameterIivEXT", "glTextureParameterIuivEXT",
                "glGetTextureParameterIivEXT", "glGetTextureParameterIiEXT",
                "glGetTextureParameterIuivEXT", "glGetTextureParameterIuiEXT",
                "glGenerateTextureMipmapEXT", "glTextureRenderbufferEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void directStateAccessTextureQueriesReturnDeterministicZeroDefaults() {
        long getTextureParameteri = GlFunctionRegistry.address("glGetTextureParameteriEXT");
        long getTextureParameteriv = GlFunctionRegistry.address("glGetTextureParameterivEXT");

        assertTrue(getTextureParameteri != 0L, "texture scalar query must have a callable trampoline address");
        assertTrue(getTextureParameteriv != 0L, "texture vector query must have a callable trampoline address");
        assertEquals(0, JNI.callI(0, 0x0DE1, 0x2801, getTextureParameteri));

        LongBuffer value = MemoryUtil.memAllocLong(1);
        try {
            value.put(0, 0x12345678L);
            JNI.callPV(0, 0x0DE1, 0x2801, MemoryUtil.memAddress(value), getTextureParameteriv);
            assertEquals(0L, value.get(0), "texture parameter readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(value);
        }
    }

    @Test
    void directStateAccessMultiTextureEntrypointsResolveThroughProvider() {
        for (String name : List.of("glCompressedMultiTexImage1DEXT",
                "glCompressedMultiTexImage2DEXT",
                "glCompressedMultiTexImage3DEXT",
                "glCompressedMultiTexSubImage1DEXT",
                "glCompressedMultiTexSubImage2DEXT",
                "glCompressedMultiTexSubImage3DEXT",
                "glCopyMultiTexImage1DEXT", "glCopyMultiTexImage2DEXT",
                "glCopyMultiTexSubImage1DEXT",
                "glCopyMultiTexSubImage2DEXT",
                "glCopyMultiTexSubImage3DEXT",
                "glGenerateMultiTexMipmapEXT",
                "glGetCompressedMultiTexImageEXT", "glGetMultiTexEnvfEXT",
                "glGetMultiTexEnvfvEXT", "glGetMultiTexEnviEXT",
                "glGetMultiTexEnvivEXT", "glGetMultiTexGendEXT",
                "glGetMultiTexGendvEXT", "glGetMultiTexGenfEXT",
                "glGetMultiTexGenfvEXT", "glGetMultiTexGeniEXT",
                "glGetMultiTexGenivEXT", "glGetMultiTexImageEXT",
                "glGetMultiTexLevelParameterfEXT",
                "glGetMultiTexLevelParameterfvEXT",
                "glGetMultiTexLevelParameteriEXT",
                "glGetMultiTexLevelParameterivEXT",
                "glGetMultiTexParameterIiEXT",
                "glGetMultiTexParameterIivEXT",
                "glGetMultiTexParameterIuiEXT",
                "glGetMultiTexParameterIuivEXT",
                "glGetMultiTexParameterfEXT",
                "glGetMultiTexParameterfvEXT",
                "glGetMultiTexParameteriEXT",
                "glGetMultiTexParameterivEXT", "glMultiTexBufferEXT",
                "glMultiTexCoordPointerEXT", "glMultiTexEnvfEXT",
                "glMultiTexEnvfvEXT", "glMultiTexEnviEXT",
                "glMultiTexEnvivEXT", "glMultiTexGendEXT",
                "glMultiTexGendvEXT", "glMultiTexGenfEXT",
                "glMultiTexGenfvEXT", "glMultiTexGeniEXT",
                "glMultiTexGenivEXT", "glMultiTexImage1DEXT",
                "glMultiTexImage2DEXT", "glMultiTexImage3DEXT",
                "glMultiTexParameterIivEXT", "glMultiTexParameterIuivEXT",
                "glMultiTexParameterfEXT", "glMultiTexParameterfvEXT",
                "glMultiTexParameteriEXT", "glMultiTexParameterivEXT",
                "glMultiTexRenderbufferEXT", "glMultiTexSubImage1DEXT",
                "glMultiTexSubImage2DEXT", "glMultiTexSubImage3DEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void directStateAccessMultiTextureQueriesReturnDeterministicZeroDefaults() {
        long getMultiTexParameteri = GlFunctionRegistry.address("glGetMultiTexParameteriEXT");

        assertTrue(getMultiTexParameteri != 0L, "multitexture scalar query must have a callable trampoline address");
        assertEquals(0, JNI.callI(0x84C0, 0x0DE1, 0x2801, getMultiTexParameteri));
    }

    @Test
    void directStateAccessMatrixAndIndexedEntrypointsResolveThroughProvider() {
        for (String name : List.of("glBindMultiTextureEXT",
                "glClientAttribDefaultEXT", "glPushClientAttribDefaultEXT",
                "glEnableClientStateIndexedEXT",
                "glDisableClientStateIndexedEXT", "glEnableClientStateiEXT",
                "glDisableClientStateiEXT", "glMatrixLoadfEXT",
                "glMatrixLoaddEXT", "glMatrixMultfEXT", "glMatrixMultdEXT",
                "glMatrixLoadIdentityEXT", "glMatrixRotatefEXT",
                "glMatrixRotatedEXT", "glMatrixScalefEXT", "glMatrixScaledEXT",
                "glMatrixTranslatefEXT", "glMatrixTranslatedEXT",
                "glMatrixOrthoEXT", "glMatrixFrustumEXT", "glMatrixPushEXT",
                "glMatrixPopEXT", "glMatrixLoadTransposefEXT",
                "glMatrixLoadTransposedEXT", "glMatrixMultTransposefEXT",
                "glMatrixMultTransposedEXT", "glGetFloatIndexedvEXT",
                "glGetFloatIndexedEXT", "glGetDoubleIndexedvEXT",
                "glGetDoubleIndexedEXT", "glGetPointerIndexedvEXT",
                "glGetPointerIndexedEXT", "glGetFloati_vEXT",
                "glGetFloatiEXT", "glGetDoublei_vEXT", "glGetDoubleiEXT",
                "glGetPointeri_vEXT", "glGetPointeriEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void directStateAccessIndexedQueriesReturnDeterministicZeroDefaults() {
        long getFloatIndexed = GlFunctionRegistry.address("glGetFloatIndexedEXT");
        long getFloatIndexedv = GlFunctionRegistry.address("glGetFloatIndexedvEXT");
        long getDoubleIndexedv = GlFunctionRegistry.address("glGetDoubleIndexedvEXT");
        long getPointerIndexed = GlFunctionRegistry.address("glGetPointerIndexedEXT");
        long getPointeriV = GlFunctionRegistry.address("glGetPointeri_vEXT");

        assertTrue(getFloatIndexed != 0L, "indexed float scalar query must have a callable trampoline address");
        assertTrue(getFloatIndexedv != 0L, "indexed float vector query must have a callable trampoline address");
        assertTrue(getDoubleIndexedv != 0L, "indexed double vector query must have a callable trampoline address");
        assertTrue(getPointerIndexed != 0L, "indexed pointer scalar query must have a callable trampoline address");
        assertTrue(getPointeriV != 0L, "indexed pointer vector query must have a callable trampoline address");

        assertEquals(0.0f, JNI.callF(0x0BA2, 0, 0, getFloatIndexed));
        assertEquals(0L, JNI.callP(0x0BA2, 0, getPointerIndexed));

        FloatBuffer floatValue = MemoryUtil.memAllocFloat(1);
        LongBuffer doubleValue = MemoryUtil.memAllocLong(1);
        LongBuffer pointerValue = MemoryUtil.memAllocLong(1);
        try {
            floatValue.put(0, 42.0f);
            doubleValue.put(0, Double.doubleToRawLongBits(42.0));
            pointerValue.put(0, 0x12345678L);

            JNI.callPV(0x0BA2, 0, MemoryUtil.memAddress(floatValue), getFloatIndexedv);
            JNI.callPV(0x0BA2, 0, MemoryUtil.memAddress(doubleValue), getDoubleIndexedv);
            JNI.callPV(0x0BA2, 0, MemoryUtil.memAddress(pointerValue), getPointeriV);

            assertEquals(0.0f, floatValue.get(0), "indexed float readback must clear the caller buffer");
            assertEquals(0L, doubleValue.get(0), "indexed double readback must clear the caller buffer");
            assertEquals(0L, pointerValue.get(0), "indexed pointer readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(floatValue);
            MemoryUtil.memFree(doubleValue);
            MemoryUtil.memFree(pointerValue);
        }
    }

    @Test
    void directStateAccessProgramEntrypointsResolveThroughProvider() {
        for (String name : List.of("glNamedProgramStringEXT",
                "glNamedProgramLocalParameter4dEXT",
                "glNamedProgramLocalParameter4dvEXT",
                "glNamedProgramLocalParameter4fEXT",
                "glNamedProgramLocalParameter4fvEXT",
                "glNamedProgramLocalParameters4fvEXT",
                "glNamedProgramLocalParameterI4iEXT",
                "glNamedProgramLocalParameterI4ivEXT",
                "glNamedProgramLocalParametersI4ivEXT",
                "glNamedProgramLocalParameterI4uiEXT",
                "glNamedProgramLocalParameterI4uivEXT",
                "glNamedProgramLocalParametersI4uivEXT",
                "glGetNamedProgramLocalParameterdvEXT",
                "glGetNamedProgramLocalParameterfvEXT",
                "glGetNamedProgramLocalParameterIivEXT",
                "glGetNamedProgramLocalParameterIuivEXT",
                "glGetNamedProgramivEXT", "glGetNamedProgramiEXT",
                "glGetNamedProgramStringEXT", "glProgramUniform1fEXT",
                "glProgramUniform2fEXT", "glProgramUniform3fEXT",
                "glProgramUniform4fEXT", "glProgramUniform1iEXT",
                "glProgramUniform2iEXT", "glProgramUniform3iEXT",
                "glProgramUniform4iEXT", "glProgramUniform1uiEXT",
                "glProgramUniform2uiEXT", "glProgramUniform3uiEXT",
                "glProgramUniform4uiEXT", "glProgramUniform1fvEXT",
                "glProgramUniform2fvEXT", "glProgramUniform3fvEXT",
                "glProgramUniform4fvEXT", "glProgramUniform1ivEXT",
                "glProgramUniform2ivEXT", "glProgramUniform3ivEXT",
                "glProgramUniform4ivEXT", "glProgramUniform1uivEXT",
                "glProgramUniform2uivEXT", "glProgramUniform3uivEXT",
                "glProgramUniform4uivEXT", "glProgramUniformMatrix2fvEXT",
                "glProgramUniformMatrix3fvEXT", "glProgramUniformMatrix4fvEXT",
                "glProgramUniformMatrix2x3fvEXT",
                "glProgramUniformMatrix3x2fvEXT",
                "glProgramUniformMatrix2x4fvEXT",
                "glProgramUniformMatrix4x2fvEXT",
                "glProgramUniformMatrix3x4fvEXT",
                "glProgramUniformMatrix4x3fvEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void directStateAccessNamedProgramQueriesReturnDeterministicZeroDefaults() {
        long getNamedProgrami = GlFunctionRegistry.address("glGetNamedProgramiEXT");
        long getNamedProgramiv = GlFunctionRegistry.address("glGetNamedProgramivEXT");
        long getNamedProgramLocalParameterfv = GlFunctionRegistry.address("glGetNamedProgramLocalParameterfvEXT");
        long getNamedProgramLocalParameterdv = GlFunctionRegistry.address("glGetNamedProgramLocalParameterdvEXT");
        long getNamedProgramLocalParameterIiv = GlFunctionRegistry.address("glGetNamedProgramLocalParameterIivEXT");

        assertTrue(getNamedProgrami != 0L, "named program scalar query must have a callable trampoline address");
        assertTrue(getNamedProgramiv != 0L, "named program vector query must have a callable trampoline address");
        assertTrue(getNamedProgramLocalParameterfv != 0L, "named program float local query must have a callable trampoline address");
        assertTrue(getNamedProgramLocalParameterdv != 0L, "named program double local query must have a callable trampoline address");
        assertTrue(getNamedProgramLocalParameterIiv != 0L, "named program integer local query must have a callable trampoline address");

        assertEquals(0, JNI.callI(0, 0x8620, 0x8627, getNamedProgrami));

        IntBuffer intValue = MemoryUtil.memAllocInt(1);
        FloatBuffer floatValue = MemoryUtil.memAllocFloat(1);
        LongBuffer doubleValue = MemoryUtil.memAllocLong(1);
        try {
            intValue.put(0, 0x12345678);
            floatValue.put(0, 42.0f);
            doubleValue.put(0, Double.doubleToRawLongBits(42.0));

            JNI.callPV(0, 0x8620, 0x8627, MemoryUtil.memAddress(intValue), getNamedProgramiv);
            JNI.callPV(0, 0x8620, 0, MemoryUtil.memAddress(floatValue), getNamedProgramLocalParameterfv);
            JNI.callPV(0, 0x8620, 0, MemoryUtil.memAddress(doubleValue), getNamedProgramLocalParameterdv);
            JNI.callPV(0, 0x8620, 0, MemoryUtil.memAddress(intValue), getNamedProgramLocalParameterIiv);

            assertEquals(0, intValue.get(0), "named program integer readback must clear the caller buffer");
            assertEquals(0.0f, floatValue.get(0), "named program float readback must clear the caller buffer");
            assertEquals(0L, doubleValue.get(0), "named program double readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(intValue);
            MemoryUtil.memFree(floatValue);
            MemoryUtil.memFree(doubleValue);
        }
    }

    @Test
    void directStateAccessVertexArrayEntrypointsResolveThroughProvider() {
        for (String name : List.of("glGetCompressedTextureImageEXT",
                "glVertexArrayVertexOffsetEXT", "glVertexArrayColorOffsetEXT",
                "glVertexArrayEdgeFlagOffsetEXT", "glVertexArrayIndexOffsetEXT",
                "glVertexArrayNormalOffsetEXT",
                "glVertexArrayTexCoordOffsetEXT",
                "glVertexArrayMultiTexCoordOffsetEXT",
                "glVertexArrayFogCoordOffsetEXT",
                "glVertexArraySecondaryColorOffsetEXT",
                "glVertexArrayVertexAttribOffsetEXT",
                "glVertexArrayVertexAttribIOffsetEXT",
                "glEnableVertexArrayEXT", "glDisableVertexArrayEXT",
                "glEnableVertexArrayAttribEXT",
                "glDisableVertexArrayAttribEXT",
                "glGetVertexArrayIntegervEXT",
                "glGetVertexArrayIntegerEXT",
                "glGetVertexArrayPointervEXT",
                "glGetVertexArrayPointerEXT",
                "glGetVertexArrayIntegeri_vEXT",
                "glGetVertexArrayIntegeriEXT",
                "glGetVertexArrayPointeri_vEXT",
                "glGetVertexArrayPointeriEXT")) {
            assertTrue(GlFunctionRegistry.isRegistered(name), name + " must resolve through the emulated GL provider");
            assertTrue(GlFunctionRegistry.address(name) != 0L, name + " must have a callable trampoline address");
        }
    }

    @Test
    void directStateAccessVertexArrayQueriesReturnDeterministicZeroDefaults() {
        long getCompressedTextureImage = GlFunctionRegistry.address("glGetCompressedTextureImageEXT");
        long getVertexArrayInteger = GlFunctionRegistry.address("glGetVertexArrayIntegerEXT");
        long getVertexArrayIntegerv = GlFunctionRegistry.address("glGetVertexArrayIntegervEXT");
        long getVertexArrayPointer = GlFunctionRegistry.address("glGetVertexArrayPointerEXT");
        long getVertexArrayPointeriV = GlFunctionRegistry.address("glGetVertexArrayPointeri_vEXT");

        assertTrue(getCompressedTextureImage != 0L, "compressed texture readback must have a callable trampoline address");
        assertTrue(getVertexArrayInteger != 0L, "vertex array scalar query must have a callable trampoline address");
        assertTrue(getVertexArrayIntegerv != 0L, "vertex array vector query must have a callable trampoline address");
        assertTrue(getVertexArrayPointer != 0L, "vertex array pointer query must have a callable trampoline address");
        assertTrue(getVertexArrayPointeriV != 0L, "indexed vertex array pointer query must have a callable trampoline address");

        assertEquals(0, JNI.callI(0, 0x8074, getVertexArrayInteger));
        assertEquals(0L, JNI.callP(0, 0x8074, getVertexArrayPointer));

        ByteBuffer compressedPixel = MemoryUtil.memAlloc(1);
        IntBuffer intValue = MemoryUtil.memAllocInt(1);
        LongBuffer pointerValue = MemoryUtil.memAllocLong(1);
        try {
            compressedPixel.put(0, (byte) 0x7F);
            intValue.put(0, 0x12345678);
            pointerValue.put(0, 0x12345678L);

            JNI.callPV(0, 0x0DE1, 0, MemoryUtil.memAddress(compressedPixel), getCompressedTextureImage);
            JNI.callPV(0, 0x8074, MemoryUtil.memAddress(intValue), getVertexArrayIntegerv);
            JNI.callPV(0, 0, 0x8074, MemoryUtil.memAddress(pointerValue), getVertexArrayPointeriV);

            assertEquals(0, compressedPixel.get(0), "compressed texture readback must clear the caller buffer");
            assertEquals(0, intValue.get(0), "vertex array integer readback must clear the caller buffer");
            assertEquals(0L, pointerValue.get(0), "indexed vertex array pointer readback must clear the caller buffer");
        } finally {
            MemoryUtil.memFree(compressedPixel);
            MemoryUtil.memFree(intValue);
            MemoryUtil.memFree(pointerValue);
        }
    }

    @Test
    void framebufferCompleteConstantMatchesOpenGlValue() throws Exception {
        var field = GlFunctionRegistry.class.getDeclaredField("GL_FRAMEBUFFER_COMPLETE");
        field.setAccessible(true);

        assertEquals(0x8CD5, field.getInt(null),
                "glCheckFramebufferStatus must report GL_FRAMEBUFFER_COMPLETE, not a color attachment enum");
    }

    /** Unknown names must resolve to 0 so capability flags stay honest. */
    @Test
    void unknownFunctionsResolveAbsentWithoutNativeAccess() {
        assertEquals(0L, GlFunctionRegistry.address("glDefinitelyNotARealFunction"));
        assertEquals(0L, GlFunctionRegistry.address("wglGetCurrentContext"));
    }
}
