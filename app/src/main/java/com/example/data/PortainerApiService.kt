package com.example.data

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

interface PortainerApi {
    @POST("api/auth")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/endpoints")
    suspend fun getEndpoints(@Header("Authorization") token: String): List<PortainerEndpoint>

    @GET("api/stacks")
    suspend fun getStacks(@Header("Authorization") token: String): List<PortainerStack>

    @POST("api/stacks/{id}/start")
    suspend fun startStack(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Query("endpointId") endpointId: Int
    ): ResponseBody

    @POST("api/stacks/{id}/stop")
    suspend fun stopStack(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Query("endpointId") endpointId: Int
    ): ResponseBody

    @DELETE("api/stacks/{id}")
    suspend fun removeStack(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Query("endpointId") endpointId: Int
    ): ResponseBody

    @GET("api/stacks/{id}/file")
    suspend fun getStackFile(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): PortainerStackFile
    
    @PUT("api/stacks/{id}")
    suspend fun updateStack(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Query("endpointId") endpointId: Int,
        @Body request: UpdateStackRequest
    ): ResponseBody
    
    @POST("api/stacks/create/standalone/string")
    suspend fun createStackString(
        @Header("Authorization") token: String,
        @Query("endpointId") endpointId: Int,
        @Body request: CreateStackStringRequest
    ): ResponseBody
    
    @GET("api/endpoints/{endpointId}/docker/containers/json")
    suspend fun getContainers(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Query("all") all: Boolean = true
    ): List<PortainerContainer>

    @GET("api/endpoints/{endpointId}/docker/containers/{containerId}/stats?stream=false")
    suspend fun getContainerStats(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String
    ): ContainerStatsResponse

    @POST("api/endpoints/{endpointId}/docker/containers/{containerId}/start")
    suspend fun startContainer(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String
    ): ResponseBody

    @POST("api/endpoints/{endpointId}/docker/containers/{containerId}/stop")
    suspend fun stopContainer(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String
    ): ResponseBody

    @POST("api/endpoints/{endpointId}/docker/containers/{containerId}/restart")
    suspend fun restartContainer(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String
    ): ResponseBody

    @POST("api/endpoints/{endpointId}/docker/images/create")
    suspend fun pullImage(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Query("fromImage") fromImage: String
    ): ResponseBody

    @POST("api/endpoints/{endpointId}/docker/containers/create")
    suspend fun createContainer(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Query("name") name: String,
        @Body request: CreateContainerRequest
    ): CreateContainerResponse

    @POST("api/endpoints/{endpointId}/docker/containers/{containerId}/pause")
    suspend fun pauseContainer(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String
    ): ResponseBody

    @POST("api/endpoints/{endpointId}/docker/containers/{containerId}/unpause")
    suspend fun unpauseContainer(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String
    ): ResponseBody

    @POST("api/endpoints/{endpointId}/docker/containers/{containerId}/kill")
    suspend fun killContainer(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String
    ): ResponseBody

    @DELETE("api/endpoints/{endpointId}/docker/containers/{containerId}")
    suspend fun removeContainer(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String,
        @Query("force") force: Boolean = true,
        @Query("v") removeVolumes: Boolean = false
    ): ResponseBody

    @GET("api/endpoints/{endpointId}/docker/volumes")
    suspend fun getVolumes(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int
    ): PortainerVolumeResponse

    @DELETE("api/endpoints/{endpointId}/docker/volumes/{volumeName}")
    suspend fun removeVolume(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("volumeName") volumeName: String
    ): ResponseBody

    @POST("api/endpoints/{endpointId}/docker/volumes/prune")
    suspend fun pruneVolumes(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int
    ): ResponseBody

    @GET("api/endpoints/{endpointId}/docker/containers/{containerId}/logs")
    suspend fun getContainerLogs(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String,
        @Query("stdout") stdout: Int = 1,
        @Query("stderr") stderr: Int = 1,
        @Query("timestamps") timestamps: Int = 0,
        @Query("tail") tail: Int = 100
    ): ResponseBody
    
    @POST("api/endpoints/{endpointId}/docker/containers/{containerId}/exec")
    suspend fun createExec(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("containerId") containerId: String,
        @Body request: CreateExecRequest
    ): CreateExecResponse

    @POST("api/endpoints/{endpointId}/docker/exec/{execId}/start")
    suspend fun startExec(
        @Header("Authorization") token: String,
        @Path("endpointId") endpointId: Int,
        @Path("execId") execId: String,
        @Body request: StartExecRequest
    ): ResponseBody
    
    @GET("api/templates")
    suspend fun getTemplates(
        @Header("Authorization") token: String
    ): PortainerTemplateResponse

    @GET("api/custom_templates")
    suspend fun getCustomTemplates(
        @Header("Authorization") token: String
    ): List<PortainerCustomTemplate>

    @GET("api/custom_templates/{id}/file")
    suspend fun getCustomTemplateFile(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): PortainerCustomTemplateFile

    @PUT("api/custom_templates/{id}")
    suspend fun updateCustomTemplate(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: UpdateCustomTemplateRequest
    ): ResponseBody
}

data class UpdateCustomTemplateRequest(
    val Title: String,
    val Description: String,
    val FileContent: String,
    val Note: String = ""
)

data class PortainerCustomTemplateFile(
    val FileContent: String?
)

data class CreateExecRequest(
    val AttachStdin: Boolean = false,
    val AttachStdout: Boolean = true,
    val AttachStderr: Boolean = true,
    val Tty: Boolean = false,
    val Env: List<String>? = null,
    val Cmd: List<String>
)

data class CreateExecResponse(val Id: String)

data class StartExecRequest(
    val Detach: Boolean = false,
    val Tty: Boolean = false
)

data class PortainerStackFile(
    val StackFileContent: String?
)

data class UpdateStackRequest(
    val env: List<PortainerEnvVar> = emptyList(),
    val prunable: Boolean = false,
    val pullImage: Boolean = true,
    val stackFileContent: String
)

data class CreateStackStringRequest(
    val name: String,
    val env: List<PortainerEnvVar> = emptyList(),
    val stackFileContent: String
)

data class PortainerEnvVar(
    val name: String,
    val value: String
)

data class PortainerTemplateResponse(
    val version: String?,
    val templates: List<PortainerTemplate>?
)

data class PortainerVolumeResponse(
    val Volumes: List<PortainerVolume>?
)

data class PortainerVolume(
    val Name: String,
    val Driver: String,
    val Mountpoint: String
)

data class PortainerTemplate(
    val Id: Int?,
    val type: Int?,
    val title: String?,
    val name: String?,
    val description: String?,
    val image: String?,
    val logo: String?,
    val ports: List<String>?,
    val env: List<PortainerTemplateEnv>?,
    val repository: PortainerTemplateRepository?
)

data class PortainerTemplateEnv(
    val name: String,
    val label: String?,
    val description: String?,
    val default: String?
)

data class PortainerTemplateRepository(
    val url: String?,
    val stackfile: String?
)

data class PortainerCustomTemplate(
    val Id: Int,
    val Title: String?,
    val Description: String?,
    val Type: Int,
    val FileContent: String?,
    val Logo: String?
)

data class CreateContainerRequest(
    val Image: String,
    val ExposedPorts: Map<String, Any>? = null,
    val HostConfig: HostConfig? = null
)

data class HostConfig(
    val PortBindings: Map<String, List<PortBinding>>? = null
)

data class PortBinding(
    val HostPort: String
)

data class CreateContainerResponse(
    val Id: String,
    val Warnings: List<String>?
)

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val jwt: String)

data class PortainerEndpoint(
    val Id: Int,
    val Name: String,
    val URL: String,
    val Status: Int,
    val Snapshots: List<Snapshot>?
)

data class Snapshot(
    val DockerVersion: String?,
    val ContainersCount: Int?,
    val RunningContainersCount: Int?,
    val StoppedContainersCount: Int?
)

data class PortainerStack(
    val Id: Int,
    val Name: String,
    val Type: Int,
    val EndpointId: Int,
    val Status: Int,
    val Env: List<PortainerEnvVar>? = null
)

data class PortainerContainer(
    val Id: String,
    val Names: List<String>?,
    val Image: String?,
    val State: String?,
    val Status: String?,
    val Labels: Map<String, String>? = null,
    val Mounts: List<PortainerMount>? = null
)

data class PortainerMount(
    val Type: String?,
    val Name: String?,
    val Source: String?,
    val Destination: String?
)


data class ContainerStatsResponse(
    val cpu_stats: CpuStats?,
    val precpu_stats: CpuStats?,
    val memory_stats: MemoryStats?
)

data class CpuStats(
    val cpu_usage: CpuUsage?,
    val system_cpu_usage: Long?,
    val online_cpus: Int?
)

data class CpuUsage(
    val total_usage: Long?
)

data class MemoryStats(
    val usage: Long?,
    val limit: Long?
)

object PortainerApiClient {
    fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    fun create(baseUrl: String): PortainerApi {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PortainerApi::class.java)
    }
}
