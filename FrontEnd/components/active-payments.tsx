"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { RefreshCw, Clock, Mail, Phone, CreditCard } from "lucide-react"
import { useToast } from "@/hooks/use-toast"

interface ActivePayment {
  phone: string
  method: string
  paymentId: string
  payerEmail: string
  amount: string
  messageId: string
  paymentTs: string
  status: string
  subject: string
  merchantName: string
  _redisKey: string
}

interface ActivePaymentsResponse {
  limit: number
  found: number
  payments: ActivePayment[]
}

export function ActivePayments() {
  const [payments, setPayments] = useState<ActivePayment[]>([])
  const [loading, setLoading] = useState(true)
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date())
  const { toast } = useToast()

  const fetchActivePayments = async () => {
    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
      const response = await fetch(`${baseUrl}/api/admin/active`)

      if (!response.ok) {
        throw new Error("Failed to fetch active payments")
      }

      const data: ActivePaymentsResponse = await response.json()
      setPayments(data.payments || [])
      setLastUpdated(new Date())
    } catch (error) {
      console.error("Error fetching active payments:", error)
      toast({
        title: "Error",
        description: "Failed to fetch active payments. Please check your connection.",
        variant: "destructive",
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchActivePayments()
    const interval = setInterval(fetchActivePayments, 10000) // Refresh every 10 seconds
    return () => clearInterval(interval)
  }, [])

  const formatDate = (dateString: string) => {
    try {
      return new Date(dateString).toLocaleString()
    } catch {
      return dateString
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Active Payments</h2>
          <p className="text-slate-600">Monitor your live payment activities</p>
        </div>
        <div className="flex items-center gap-4">
          <div className="text-sm text-slate-500">Last updated: {lastUpdated.toLocaleTimeString()}</div>
          <Button
            onClick={fetchActivePayments}
            disabled={loading}
            size="sm"
            className="bg-gradient-to-r from-purple-600 to-purple-800 hover:from-purple-700 hover:to-purple-900"
          >
            <RefreshCw className={`h-4 w-4 mr-2 ${loading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
        </div>
      </div>

      {loading && payments.length === 0 ? (
        <div className="flex items-center justify-center py-12">
          <div className="text-center">
            <RefreshCw className="h-8 w-8 animate-spin text-purple-600 mx-auto mb-4" />
            <p className="text-slate-600">Loading active payments...</p>
          </div>
        </div>
      ) : payments.length === 0 ? (
        <Card className="border-purple-100">
          <CardContent className="flex items-center justify-center py-12">
            <div className="text-center">
              <CreditCard className="h-12 w-12 text-slate-400 mx-auto mb-4" />
              <h3 className="text-lg font-medium text-slate-800 mb-2">No Active Payments</h3>
              <p className="text-slate-600">There are currently no active payment transactions.</p>
            </div>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4">
          {payments.map((payment, index) => (
            <Card key={payment._redisKey || index} className="border-purple-100 hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="text-lg text-slate-800">{payment.subject || "Payment Transaction"}</CardTitle>
                  <Badge variant="secondary" className="bg-yellow-100 text-yellow-700">
                    {payment.status || "Active"}
                  </Badge>
                </div>
                <CardDescription className="flex items-center gap-2">
                  <Clock className="h-4 w-4" />
                  {formatDate(payment.paymentTs)}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  <div className="space-y-2">
                    <div className="flex items-center gap-2 text-sm">
                      <Mail className="h-4 w-4 text-slate-500" />
                      <span className="font-medium">Email:</span>
                    </div>
                    <p className="text-sm text-slate-700 pl-6">{payment.payerEmail || "Not provided"}</p>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center gap-2 text-sm">
                      <CreditCard className="h-4 w-4 text-slate-500" />
                      <span className="font-medium">Amount:</span>
                    </div>
                    <p className="text-sm text-slate-700 pl-6">
                      {payment.amount ? `₹${payment.amount}` : "Not specified"}
                    </p>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center gap-2 text-sm">
                      <Phone className="h-4 w-4 text-slate-500" />
                      <span className="font-medium">Method:</span>
                    </div>
                    <p className="text-sm text-slate-700 pl-6">{payment.method || "Not specified"}</p>
                  </div>
                </div>

                {payment.paymentId && (
                  <div className="mt-4 p-3 bg-slate-50 rounded-lg">
                    <p className="text-xs text-slate-500 mb-1">Payment ID</p>
                    <p className="text-sm font-mono text-slate-700">{payment.paymentId}</p>
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
