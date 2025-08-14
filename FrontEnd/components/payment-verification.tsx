"use client"

import type React from "react"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { TestTube, CheckCircle, XCircle, Mail, CreditCard, Calendar, User, Phone, Building } from "lucide-react"
import { useToast } from "@/hooks/use-toast"

interface VerificationRequest {
  email: string
  amount: string
}

interface VerificationResponse {
  success: boolean
  message: string
  payment?: {
    paymentId: string
    amount: string
    paidOn: string
    payerEmail: string
    phone: string
    method: string
    merchantName: string
    subject: string
    messageId: string
  }
}

export function PaymentVerification() {
  const [formData, setFormData] = useState<VerificationRequest>({
    email: "",
    amount: "",
  })
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<VerificationResponse | null>(null)
  const { toast } = useToast()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!formData.email || !formData.amount) {
      toast({
        title: "Validation Error",
        description: "Please fill in both email and amount fields.",
        variant: "destructive",
      })
      return
    }

    setLoading(true)
    setResult(null)

    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
      const response = await fetch(`${baseUrl}/api/payments/verify`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(formData),
      })

      const data: VerificationResponse = await response.json()
      setResult(data)

      if (data.success) {
        toast({
          title: "Verification Successful",
          description: data.message,
        })
      } else {
        toast({
          title: "Verification Failed",
          description: data.message,
          variant: "destructive",
        })
      }
    } catch (error) {
      console.error("Error verifying payment:", error)
      toast({
        title: "Error",
        description: "Failed to verify payment. Please check your connection.",
        variant: "destructive",
      })
      setResult({
        success: false,
        message: "Network error occurred while verifying payment.",
      })
    } finally {
      setLoading(false)
    }
  }

  const handleReset = () => {
    setFormData({ email: "", amount: "" })
    setResult(null)
  }

  const formatDate = (dateString: string) => {
    try {
      return new Date(dateString).toLocaleString()
    } catch {
      return dateString
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Payment Verification</h2>
        <p className="text-slate-600">Test the /verify endpoint with email and amount</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Verification Form */}
        <Card className="border-purple-100">
          <CardHeader>
            <div className="flex items-center gap-3">
              <TestTube className="h-5 w-5 text-purple-600" />
              <div>
                <CardTitle className="text-lg text-slate-800">Test Payment Verification</CardTitle>
                <CardDescription>Enter email and amount to verify payment</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email" className="text-sm font-medium text-slate-700">
                  Email Address
                </Label>
                <div className="relative">
                  <Mail className="absolute left-3 top-3 h-4 w-4 text-slate-400" />
                  <Input
                    id="email"
                    type="email"
                    placeholder="Enter payer email address"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    className="pl-10 border-purple-200 focus:border-purple-400"
                    required
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="amount" className="text-sm font-medium text-slate-700">
                  Amount
                </Label>
                <div className="relative">
                  <CreditCard className="absolute left-3 top-3 h-4 w-4 text-slate-400" />
                  <Input
                    id="amount"
                    type="text"
                    placeholder="Enter payment amount"
                    value={formData.amount}
                    onChange={(e) => setFormData({ ...formData, amount: e.target.value })}
                    className="pl-10 border-purple-200 focus:border-purple-400"
                    required
                  />
                </div>
              </div>

              <div className="flex gap-3 pt-4">
                <Button
                  type="submit"
                  disabled={loading}
                  className="flex-1 bg-gradient-to-r from-purple-600 to-purple-800 hover:from-purple-700 hover:to-purple-900"
                >
                  {loading ? (
                    <>
                      <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                      Verifying...
                    </>
                  ) : (
                    <>
                      <TestTube className="h-4 w-4 mr-2" />
                      Verify Payment
                    </>
                  )}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleReset}
                  className="border-purple-200 bg-transparent"
                >
                  Reset
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>

        {/* API Information */}
        <Card className="border-purple-100">
          <CardHeader>
            <CardTitle className="text-lg text-slate-800">API Endpoint Information</CardTitle>
            <CardDescription>Details about the verification endpoint</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="p-4 bg-slate-50 rounded-lg">
              <p className="text-sm font-medium text-slate-700 mb-2">Endpoint</p>
              <code className="text-sm text-purple-700 bg-purple-50 px-2 py-1 rounded">POST /api/payments/verify</code>
            </div>

            <div className="p-4 bg-slate-50 rounded-lg">
              <p className="text-sm font-medium text-slate-700 mb-2">Request Body</p>
              <pre className="text-xs text-slate-600 bg-white p-2 rounded border overflow-x-auto">
                {`{
  "email": "user@example.com",
  "amount": "1000.00"
}`}
              </pre>
            </div>

            <div className="p-4 bg-slate-50 rounded-lg">
              <p className="text-sm font-medium text-slate-700 mb-2">Expected Response</p>
              <pre className="text-xs text-slate-600 bg-white p-2 rounded border overflow-x-auto">
                {`{
  "success": true,
  "message": "Payment verified",
  "payment": {
    "paymentId": "pay_ABCDEFGH",
    "amount": "1000.00",
    "paidOn": "2025-08-14T07:10:50Z",
    "payerEmail": "user@example.com",
    "phone": "123456789",
    "method": "UPI",
    "merchantName": "NAME",
    "subject": "Payment successful",
    "messageId": "<message@mail.com>"
  }
}`}
              </pre>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Verification Result */}
      {result && (
        <Card className={`border-2 ${result.success ? "border-green-200 bg-green-50" : "border-red-200 bg-red-50"}`}>
          <CardHeader>
            <div className="flex items-center gap-3">
              {result.success ? (
                <CheckCircle className="h-6 w-6 text-green-600" />
              ) : (
                <XCircle className="h-6 w-6 text-red-600" />
              )}
              <div>
                <CardTitle className={`text-lg ${result.success ? "text-green-800" : "text-red-800"}`}>
                  Verification {result.success ? "Successful" : "Failed"}
                </CardTitle>
                <CardDescription className={result.success ? "text-green-600" : "text-red-600"}>
                  {result.message}
                </CardDescription>
              </div>
            </div>
          </CardHeader>

          {result.success && result.payment && (
            <CardContent>
              <Separator className="mb-6" />
              <div className="space-y-6">
                <div>
                  <h4 className="text-lg font-semibold text-slate-800 mb-4">Payment Details</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="space-y-4">
                      <div className="flex items-center gap-3">
                        <CreditCard className="h-4 w-4 text-slate-500" />
                        <div>
                          <p className="text-sm font-medium text-slate-600">Payment ID</p>
                          <p className="text-sm text-slate-800 font-mono">{result.payment.paymentId}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3">
                        <CreditCard className="h-4 w-4 text-slate-500" />
                        <div>
                          <p className="text-sm font-medium text-slate-600">Amount</p>
                          <p className="text-sm text-slate-800">₹{result.payment.amount}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3">
                        <Calendar className="h-4 w-4 text-slate-500" />
                        <div>
                          <p className="text-sm font-medium text-slate-600">Paid On</p>
                          <p className="text-sm text-slate-800">{formatDate(result.payment.paidOn)}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3">
                        <CreditCard className="h-4 w-4 text-slate-500" />
                        <div>
                          <p className="text-sm font-medium text-slate-600">Payment Method</p>
                          <Badge variant="secondary" className="bg-purple-100 text-purple-700">
                            {result.payment.method}
                          </Badge>
                        </div>
                      </div>
                    </div>

                    <div className="space-y-4">
                      <div className="flex items-center gap-3">
                        <Mail className="h-4 w-4 text-slate-500" />
                        <div>
                          <p className="text-sm font-medium text-slate-600">Payer Email</p>
                          <p className="text-sm text-slate-800">{result.payment.payerEmail}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3">
                        <Phone className="h-4 w-4 text-slate-500" />
                        <div>
                          <p className="text-sm font-medium text-slate-600">Phone</p>
                          <p className="text-sm text-slate-800">{result.payment.phone || "Not provided"}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3">
                        <Building className="h-4 w-4 text-slate-500" />
                        <div>
                          <p className="text-sm font-medium text-slate-600">Merchant</p>
                          <p className="text-sm text-slate-800">{result.payment.merchantName}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3">
                        <User className="h-4 w-4 text-slate-500" />
                        <div>
                          <p className="text-sm font-medium text-slate-600">Subject</p>
                          <p className="text-sm text-slate-800">{result.payment.subject}</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="p-4 bg-white rounded-lg border">
                  <p className="text-sm font-medium text-slate-600 mb-2">Message ID</p>
                  <p className="text-xs font-mono text-slate-700 break-all">{result.payment.messageId}</p>
                </div>
              </div>
            </CardContent>
          )}
        </Card>
      )}
    </div>
  )
}
