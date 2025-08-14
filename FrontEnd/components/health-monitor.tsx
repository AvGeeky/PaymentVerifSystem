"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { RefreshCw, CheckCircle, XCircle, AlertTriangle, Activity, Clock, Database, Zap } from "lucide-react"
import { useToast } from "@/hooks/use-toast"

interface HealthResponse {
  lastHeartbeat: string
  ageSeconds: number
  key: string
  dependencies: {
    keepAliveScheduler: boolean
    heartbeatScheduler: boolean
    sweepScheduler: boolean
    workerPool: boolean
    inbox: boolean
    store: boolean
    running: boolean
  }
  status: "UP" | "DOWN"
}

export function HealthMonitor() {
  const [healthData, setHealthData] = useState<HealthResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date())
  const { toast } = useToast()

  const fetchHealthData = async () => {
    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
      const response = await fetch(`${baseUrl}/api/admin/health`)

      // if (!response.ok) {
      //   throw new Error("Failed to fetch health data")
      // }

      const data: HealthResponse = await response.json()
      setHealthData(data)
      setLastUpdated(new Date())
    } catch (error) {
      console.error("Error fetching health data:", error)
      toast({
        title: "Error",
        description: "Failed to fetch system health. Please check your connection.",
        variant: "destructive",
      })
      setHealthData(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchHealthData()
    const interval = setInterval(fetchHealthData, 60000) // Refresh every 10 seconds
    return () => clearInterval(interval)
  }, [])

  const formatDate = (dateString: string) => {
    try {
      return new Date(dateString).toLocaleString()
    } catch {
      return dateString
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case "UP":
        return "text-green-600"
      case "DOWN":
        return "text-red-600"
      default:
        return "text-yellow-600"
    }
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "UP":
        return <CheckCircle className="h-6 w-6 text-green-600" />
      case "DOWN":
        return <XCircle className="h-6 w-6 text-red-600" />
      default:
        return <AlertTriangle className="h-6 w-6 text-yellow-600" />
    }
  }

  const getDependencyIcon = (name: string) => {
    switch (name) {
      case "keepAliveScheduler":
      case "heartbeatScheduler":
      case "sweepScheduler":
        return <Clock className="h-4 w-4" />
      case "workerPool":
        return <Zap className="h-4 w-4" />
      case "inbox":
        return <Activity className="h-4 w-4" />
      case "store":
        return <Database className="h-4 w-4" />
      default:
        return <CheckCircle className="h-4 w-4" />
    }
  }

  const formatDependencyName = (name: string) => {
    return name
      .replace(/([A-Z])/g, " $1")
      .replace(/^./, (str) => str.toUpperCase())
      .trim()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">System Health</h2>
          <p className="text-slate-600">Real-time status refreshed every 60s to ensure seamless operations</p>
        </div>
        <div className="flex items-center gap-4">
          <div className="text-sm text-slate-500">Last updated: {lastUpdated.toLocaleTimeString()}</div>
          <Button
            onClick={fetchHealthData}
            disabled={loading}
            size="sm"
            className="bg-gradient-to-r from-purple-600 to-purple-800 hover:from-purple-700 hover:to-purple-900"
          >
            <RefreshCw className={`h-4 w-4 mr-2 ${loading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
        </div>
      </div>

      {loading && !healthData ? (
        <div className="flex items-center justify-center py-12">
          <div className="text-center">
            <RefreshCw className="h-8 w-8 animate-spin text-purple-600 mx-auto mb-4" />
            <p className="text-slate-600">Checking system health...</p>
          </div>
        </div>
      ) : (
        <div className="grid gap-6">
          {/* Overall Status */}
          <Card className="border-purple-100">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  {healthData ? getStatusIcon(healthData.status) : <AlertTriangle className="h-6 w-6 text-gray-400" />}
                  <div>
                    <CardTitle className="text-xl text-slate-800">System Status</CardTitle>
                    <CardDescription>Overall system health monitoring</CardDescription>
                  </div>
                </div>
                <Badge
                  variant="secondary"
                  className={`${
                    healthData?.status === "UP"
                      ? "bg-green-100 text-green-700"
                      : healthData?.status === "DOWN"
                        ? "bg-red-100 text-red-700"
                        : "bg-gray-100 text-gray-700"
                  }`}
                >
                  {healthData?.status || "UNKNOWN"}
                </Badge>
              </div>
            </CardHeader>
            <CardContent>
              {healthData ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div className="space-y-2">
                    <p className="text-sm font-medium text-slate-600">Last Heartbeat</p>
                    <p className="text-lg text-slate-800">{formatDate(healthData.lastHeartbeat)}</p>
                  </div>
                  <div className="space-y-2">
                    <p className="text-sm font-medium text-slate-600">Age (Max Age: 90s)</p>
                    <p className="text-lg text-slate-800">{healthData.ageSeconds} seconds</p>
                  </div>
                </div>
              ) : (
                <div className="text-center py-8">
                  <XCircle className="h-12 w-12 text-red-400 mx-auto mb-4" />
                  <p className="text-slate-600">Unable to fetch system health data</p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Dependencies Status */}
          {healthData && (
            <Card className="border-purple-100">
              <CardHeader>
                <CardTitle className="text-lg text-slate-800">System Dependencies</CardTitle>
                <CardDescription>Status of individual system components</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {Object.entries(healthData.dependencies).map(([name, status]) => (
                    <div
                      key={name}
                      className={`p-4 rounded-lg border-2 transition-colors ${
                        status ? "border-green-200 bg-green-50" : "border-red-200 bg-red-50"
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div className={status ? "text-green-600" : "text-red-600"}>{getDependencyIcon(name)}</div>
                        <div className="flex-1">
                          <p className="font-medium text-slate-800">{formatDependencyName(name)}</p>
                          <div className="flex items-center gap-2 mt-1">
                            {status ? (
                              <CheckCircle className="h-3 w-3 text-green-600" />
                            ) : (
                              <XCircle className="h-3 w-3 text-red-600" />
                            )}
                            <span className={`text-xs font-medium ${status ? "text-green-700" : "text-red-700"}`}>
                              {status ? "Operational" : "Failed"}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {/* Health Summary */}
          {healthData && (
            <Card className="border-purple-100">
              <CardHeader>
                <CardTitle className="text-lg text-slate-800">Health Summary</CardTitle>
                <CardDescription>Quick overview of system performance</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                  <div className="text-center p-4 bg-gradient-to-br from-green-50 to-green-100 rounded-lg">
                    <CheckCircle className="h-8 w-8 text-green-600 mx-auto mb-2" />
                    <p className="text-2xl font-bold text-green-700">
                      {Object.values(healthData.dependencies).filter(Boolean).length}
                    </p>
                    <p className="text-sm text-green-600">Services Online</p>
                  </div>

                  <div className="text-center p-4 bg-gradient-to-br from-red-50 to-red-100 rounded-lg">
                    <XCircle className="h-8 w-8 text-red-600 mx-auto mb-2" />
                    <p className="text-2xl font-bold text-red-700">
                      {Object.values(healthData.dependencies).filter((status) => !status).length}
                    </p>
                    <p className="text-sm text-red-600">Services Offline</p>
                  </div>

                  <div className="text-center p-4 bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg">
                    <Activity className="h-8 w-8 text-purple-600 mx-auto mb-2" />
                    <p className="text-2xl font-bold text-purple-700">
                      {Math.round(
                        (Object.values(healthData.dependencies).filter(Boolean).length /
                          Object.values(healthData.dependencies).length) *
                          100,
                      )}
                      %
                    </p>
                    <p className="text-sm text-purple-600">Uptime</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </div>
  )
}
