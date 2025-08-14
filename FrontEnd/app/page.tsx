"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Activity, CreditCard, CheckCircle, AlertCircle, TestTube } from "lucide-react"
import { ActivePayments } from "@/components/active-payments"
import { ProcessedPayments } from "@/components/processed-payments"
import { HealthMonitor } from "@/components/health-monitor"
import { PaymentVerification } from "@/components/payment-verification"

type View = "dashboard" | "active" | "processed" | "health" | "verify"

export default function PaymentDashboard() {
  const [currentView, setCurrentView] = useState<View>("dashboard")

  const renderView = () => {
    switch (currentView) {
      case "active":
        return <ActivePayments />
      case "processed":
        return <ProcessedPayments />
      case "health":
        return <HealthMonitor />
      case "verify":
        return <PaymentVerification />
      default:
        return <DashboardOverview setView={setCurrentView} />
    }
  }

  return (
      <div className="min-h-screen bg-gradient-to-br from-purple-50 via-white to-purple-50">
        {/* Header */}
        <header className="border-b border-purple-100 bg-white/80 backdrop-blur-sm">
          <div className="container mx-auto px-6 py-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="h-8 w-8 rounded-lg bg-gradient-to-br from-purple-600 to-purple-800 flex items-center justify-center">
                  <CreditCard className="h-4 w-4 text-white" />
                </div>
                <h1 className="text-xl font-bold text-slate-800">Payment Management</h1>
              </div>
              <nav className="flex items-center gap-2">
                <Button
                    variant={currentView === "dashboard" ? "default" : "ghost"}
                    size="sm"
                    onClick={() => setCurrentView("dashboard")}
                    className="bg-gradient-to-r from-purple-600 to-purple-800 hover:from-purple-700 hover:to-purple-900"
                >
                  Overview
                </Button>
                <Button
                    variant={currentView === "active" ? "default" : "ghost"}
                    size="sm"
                    onClick={() => setCurrentView("active")}
                >
                  Active
                </Button>
                <Button
                    variant={currentView === "processed" ? "default" : "ghost"}
                    size="sm"
                    onClick={() => setCurrentView("processed")}
                >
                  Processed
                </Button>
                <Button
                    variant={currentView === "health" ? "default" : "ghost"}
                    size="sm"
                    onClick={() => setCurrentView("health")}
                >
                  Health
                </Button>
                <Button
                    variant={currentView === "verify" ? "default" : "ghost"}
                    size="sm"
                    onClick={() => setCurrentView("verify")}
                >
                  Verify
                </Button>
              </nav>
            </div>
          </div>
        </header>

        {/* Main Content */}
        <main className="container mx-auto px-6 py-8">{renderView()}</main>
      </div>
  )
}

function DashboardOverview({ setView }: { setView: (view: View) => void }) {
  const [stats, setStats] = useState({
    activeCount: 0,
    processedTodayCount: 0,
    systemHealth: "UP",
    loading: true,
    error: null as string | null,
  })

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"

        const fetchWithTimeout = async (url: string, timeout = 5000) => {
          const controller = new AbortController()
          const timeoutId = setTimeout(() => controller.abort(), timeout)

          try {
            const response = await fetch(url, { signal: controller.signal })
            clearTimeout(timeoutId)
            return response
          } catch (error) {
            clearTimeout(timeoutId)
            throw error
          }
        }

        // Fetch active payments count
        const activeResponse = await fetchWithTimeout(`${baseUrl}/api/admin/active`)
        if (!activeResponse.ok) throw new Error(`Active API returned ${activeResponse.status}`)
        const activeData = await activeResponse.json()

        // Fetch processed payments and count today's entries
        const processedResponse = await fetchWithTimeout(`${baseUrl}/api/admin/processed`)
        if (!processedResponse.ok) throw new Error(`Processed API returned ${processedResponse.status}`)
        const processedData = await processedResponse.json()

        // Count processed payments from today
        const today = new Date().toISOString().split("T")[0]
        const todayCount =
            processedData.entries?.filter((entry: any) => {
              const paymentDate = entry.payment?.paymentTs?.split("T")[0]
              return paymentDate === today
            }).length || 0

        // Fetch health status
        const healthResponse = await fetchWithTimeout(`${baseUrl}/api/admin/health`)
        if (!healthResponse.ok) throw new Error(`Health API returned ${healthResponse.status}`)
        const healthData = await healthResponse.json()

        setStats({
          activeCount: activeData.found || 0,
          processedTodayCount: todayCount,
          systemHealth: healthData.status || "UP",
          loading: false,
          error: null,
        })
      } catch (error) {
        console.error("Failed to fetch dashboard stats:", error)
        setStats((prev) => ({
          ...prev,
          loading: false,
          error: error instanceof Error ? error.message : "Failed to connect to API server",
        }))
      }
    }

    fetchStats()
    // Refresh stats every 30 seconds
    const interval = setInterval(fetchStats, 30000)
    return () => clearInterval(interval)
  }, [])

  return (
      <div className="space-y-8">
        <div>
          <h2 className="text-2xl font-bold text-slate-800 mb-2">Payment Management Overview</h2>
          <p className="text-slate-600">Monitor your payment activities and system health in real-time</p>
          {stats.error && (
              <div className="mt-4 p-4 bg-red-50 border border-red-200 rounded-lg">
                <div className="flex items-center gap-2">
                  <AlertCircle className="h-4 w-4 text-red-600" />
                  <span className="text-sm text-red-700">API Connection Error: {stats.error}</span>
                </div>
                <p className="text-xs text-red-600 mt-1">
                  Make sure your API server is running on {process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"}{" "}
                  and CORS is configured.
                </p>
              </div>
          )}
        </div>

        {/* Quick Stats */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <Card
              className="border-purple-100 hover:shadow-lg transition-shadow cursor-pointer"
              onClick={() => setView("active")}
          >
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium text-slate-600">Active Payments</CardTitle>
              <Activity className="h-4 w-4 text-purple-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-slate-800">
                {stats.loading ? "--" : stats.error ? "!" : stats.activeCount}
              </div>
              <p className="text-xs text-slate-500">Current transactions</p>
            </CardContent>
          </Card>

          <Card
              className="border-purple-100 hover:shadow-lg transition-shadow cursor-pointer"
              onClick={() => setView("processed")}
          >
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium text-slate-600">Processed Today</CardTitle>
              <CheckCircle className="h-4 w-4 text-green-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-slate-800">
                {stats.loading ? "--" : stats.error ? "!" : stats.processedTodayCount}
              </div>
              <p className="text-xs text-slate-500">Completed payments</p>
            </CardContent>
          </Card>

          <Card
              className="border-purple-100 hover:shadow-lg transition-shadow cursor-pointer"
              onClick={() => setView("health")}
          >
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium text-slate-600">System Health</CardTitle>
              <AlertCircle
                  className={`h-4 w-4 ${stats.error ? "text-red-600" : stats.systemHealth === "UP" ? "text-green-600" : "text-red-600"}`}
              />
            </CardHeader>
            <CardContent>
              <div
                  className={`text-2xl font-bold ${stats.error ? "text-red-600" : stats.systemHealth === "UP" ? "text-green-600" : "text-red-600"}`}
              >
                {stats.loading ? "--" : stats.error ? "DOWN" : stats.systemHealth}
              </div>
              <p className="text-xs text-slate-500">{stats.error ? "API unavailable" : "All systems operational"}</p>
            </CardContent>
          </Card>

          <Card
              className="border-purple-100 hover:shadow-lg transition-shadow cursor-pointer"
              onClick={() => setView("verify")}
          >
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium text-slate-600">Test Verification</CardTitle>
              <TestTube className="h-4 w-4 text-purple-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-slate-800">Test</div>
              <p className="text-xs text-slate-500">Verify payments</p>
            </CardContent>
          </Card>
        </div>

        {/* Quick Actions */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card className="border-purple-100">
            <CardHeader>
              <CardTitle className="text-lg text-slate-800">Current Transactions</CardTitle>
              <CardDescription>Monitor your live payment activities</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between p-4 bg-gradient-to-r from-purple-50 to-purple-100 rounded-lg">
                <div className="flex items-center gap-3">
                  <div className="h-2 w-2 bg-green-500 rounded-full animate-pulse"></div>
                  <span className="text-sm text-slate-700">Real-time monitoring active</span>
                </div>
                <Badge variant="secondary" className="bg-green-100 text-green-700">
                  Live
                </Badge>
              </div>
              <Button
                  onClick={() => setView("active")}
                  className="w-full bg-gradient-to-r from-purple-600 to-purple-800 hover:from-purple-700 hover:to-purple-900"
              >
                View Active Payments
              </Button>
            </CardContent>
          </Card>

          <Card className="border-purple-100">
            <CardHeader>
              <CardTitle className="text-lg text-slate-800">Recent Transactions</CardTitle>
              <CardDescription>Keep track of completed payments</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between p-4 bg-gradient-to-r from-slate-50 to-slate-100 rounded-lg">
                <div className="flex items-center gap-3">
                  <CheckCircle className="h-4 w-4 text-green-600" />
                  <span className="text-sm text-slate-700">Processing complete</span>
                </div>
                <Badge variant="secondary" className="bg-slate-100 text-slate-700">
                  Ready
                </Badge>
              </div>
              <Button
                  onClick={() => setView("processed")}
                  variant="outline"
                  className="w-full border-purple-200 hover:bg-purple-50"
              >
                View Processed Payments
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
  )
}
