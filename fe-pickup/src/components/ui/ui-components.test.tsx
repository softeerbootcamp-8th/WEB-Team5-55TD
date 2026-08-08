import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";

describe("공통 UI 컴포넌트", () => {
  it("card, input, label, button, separator를 렌더링한다", () => {
    render(
      <Card>
        <CardHeader>
          <CardTitle>제목</CardTitle>
          <CardDescription>설명</CardDescription>
        </CardHeader>
        <CardContent>
          <Label htmlFor="field">필드</Label>
          <Input id="field" placeholder="입력" />
          <Button>확인</Button>
          <Separator />
        </CardContent>
      </Card>,
    );
    expect(screen.getByText("제목")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("입력")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "확인" })).toBeInTheDocument();
  });

  it("accordion content toggles", () => {
    render(
      <Accordion type="single" collapsible>
        <AccordionItem value="one">
          <AccordionTrigger>상세 정보</AccordionTrigger>
          <AccordionContent>내용입니다.</AccordionContent>
        </AccordionItem>
      </Accordion>,
    );
    expect(screen.getByText("상세 정보")).toBeInTheDocument();
  });
});
